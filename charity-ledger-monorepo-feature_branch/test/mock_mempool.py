import json
import os
import threading
import time
from http.server import BaseHTTPRequestHandler, HTTPServer
from urllib.parse import urlparse

# Global state to mock the mempool
mempool_queue = []
# HTTP handlers and the RabbitMQ consumer thread both mutate the queue.
mempool_lock = threading.Lock()

# ---------------------------------------------------------------------------
# RabbitMQ consumer (one-sided bridge: backend -> mempool_queue -> this list)
#
# The off-chain backend publishes fully signed, on-chain-format transaction
# JSON to the "mempool_queue" RabbitMQ queue. This thread consumes those
# messages and appends them to the in-memory list that the blockchain nodes
# already poll over HTTP (GET /api/mempool/batch). The node side is untouched.
# ---------------------------------------------------------------------------

RABBITMQ_HOST = os.environ.get('RABBITMQ_HOST', 'rabbitmq')
RABBITMQ_PORT = int(os.environ.get('RABBITMQ_PORT', '5672'))
RABBITMQ_USER = os.environ.get('RABBITMQ_USERNAME', 'admin')
RABBITMQ_PASS = os.environ.get('RABBITMQ_PASSWORD', 'admin')
MEMPOOL_QUEUE_NAME = os.environ.get('MEMPOOL_QUEUE', 'mempool_queue')


def consume_rabbitmq():
    try:
        import pika
    except ImportError:
        print("[Mempool] pika not installed - RabbitMQ consumer disabled (HTTP /inject only).")
        return

    while True:
        try:
            credentials = pika.PlainCredentials(RABBITMQ_USER, RABBITMQ_PASS)
            connection = pika.BlockingConnection(pika.ConnectionParameters(
                host=RABBITMQ_HOST, port=RABBITMQ_PORT, credentials=credentials,
                heartbeat=60))
            channel = connection.channel()
            # Must match the backend's declaration exactly: durable classic queue.
            channel.queue_declare(queue=MEMPOOL_QUEUE_NAME, durable=True)

            def on_message(ch, method, properties, body):
                try:
                    tx = json.loads(body.decode('utf-8'))
                    if "type" not in tx or "txId" not in tx:
                        raise ValueError("Missing type or txId")
                    with mempool_lock:
                        mempool_queue.append(tx)
                        size = len(mempool_queue)
                    print(f"[Mempool] Consumed tx {tx['txId'][:16]}... from RabbitMQ. Queue size: {size}")
                except Exception as e:
                    print(f"[Mempool] Dropped malformed RabbitMQ message: {e}")
                ch.basic_ack(delivery_tag=method.delivery_tag)

            channel.basic_consume(queue=MEMPOOL_QUEUE_NAME, on_message_callback=on_message)
            print(f"[Mempool] RabbitMQ consumer connected to {RABBITMQ_HOST}:{RABBITMQ_PORT} queue '{MEMPOOL_QUEUE_NAME}'")
            channel.start_consuming()
        except Exception as e:
            print(f"[Mempool] RabbitMQ connection failed ({e}); retrying in 5s...")
            time.sleep(5)


class MempoolMockHandler(BaseHTTPRequestHandler):
    def do_GET(self):
        global mempool_queue

        parsed_path = urlparse(self.path)
        if parsed_path.path == '/api/mempool/batch':
            # Just return the top transactions without removing them from the queue
            # (Removal happens in /api/mempool/confirm_txs). Batch size is the
            # per-block throughput cap: 50 keeps a multi-thousand-tx soak test
            # draining at ~50-100 tx/min instead of backing up at 10/round.
            batch_size = int(os.getenv('MEMPOOL_BATCH_SIZE', '50'))
            with mempool_lock:
                top_txs = mempool_queue[:batch_size]

            response_data = {
                "batchId": 0, # Legacy field, ignored by new implementation
                "transactions": top_txs
            }

            self.send_response(200)
            self.send_header('Content-Type', 'application/json')
            self.end_headers()
            self.wfile.write(json.dumps(response_data).encode('utf-8'))
        else:
            self.send_response(404)
            self.end_headers()

    def do_POST(self):
        global mempool_queue

        parsed_path = urlparse(self.path)
        content_length = int(self.headers.get('Content-Length', 0))
        post_data = self.rfile.read(content_length)

        if parsed_path.path == '/api/mempool/confirm_txs':
            try:
                data = json.loads(post_data.decode('utf-8'))
                tx_ids_to_remove = set(data.get('txIds', []))

                with mempool_lock:
                    original_len = len(mempool_queue)
                    mempool_queue = [tx for tx in mempool_queue if tx['txId'] not in tx_ids_to_remove]
                    removed_count = original_len - len(mempool_queue)
                    queue_size = len(mempool_queue)

                print(f"[Mempool] Confirmed and deleted {removed_count} transactions. Queue size: {queue_size}")

                self.send_response(200)
                self.send_header('Content-Type', 'application/json')
                self.end_headers()
                self.wfile.write(json.dumps({"status": "success", "removed": removed_count}).encode('utf-8'))
            except Exception as e:
                self.send_response(400)
                self.end_headers()

        elif parsed_path.path == '/inject':
            try:
                tx = json.loads(post_data.decode('utf-8'))
                # Basic validation
                if "type" not in tx or "txId" not in tx:
                    raise ValueError("Missing type or txId")

                with mempool_lock:
                    mempool_queue.append(tx)
                    queue_size = len(mempool_queue)
                print(f"[Mempool] Injected transaction {tx['txId']} into queue. Queue size: {queue_size}")

                self.send_response(200)
                self.send_header('Content-Type', 'application/json')
                self.end_headers()
                self.wfile.write(json.dumps({"status": "injected", "queueSize": queue_size}).encode('utf-8'))
            except Exception as e:
                print(f"[Mempool] Failed to inject tx: {e}")
                self.send_response(400)
                self.end_headers()
                self.wfile.write(json.dumps({"error": str(e)}).encode('utf-8'))
        else:
            self.send_response(404)
            self.end_headers()

    def log_message(self, format, *args):
        # Suppress noisy standard HTTP logs
        pass

def run(server_class=HTTPServer, handler_class=MempoolMockHandler, port=8080):
    consumer = threading.Thread(target=consume_rabbitmq, daemon=True)
    consumer.start()

    server_address = ('', port)
    httpd = server_class(server_address, handler_class)
    print(f"[Mempool Mock] Starting server on port {port}...")
    print(f"  - GET  /api/mempool/batch")
    print(f"  - POST /api/mempool/confirm_txs")
    print(f"  - POST /inject (manual testing)")
    print(f"  - AMQP consumer on queue '{MEMPOOL_QUEUE_NAME}' ({RABBITMQ_HOST}:{RABBITMQ_PORT})")
    try:
        httpd.serve_forever()
    except KeyboardInterrupt:
        pass
    httpd.server_close()
    print("[Mempool Mock] Stopping server...\n")

if __name__ == '__main__':
    run()
