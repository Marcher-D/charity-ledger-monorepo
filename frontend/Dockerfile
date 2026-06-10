# ── Stage 1: Build the React app with Node ───────────────────────────────────
FROM node:20-alpine AS builder
WORKDIR /app

# Copy package files first so Docker caches npm install separately from source
COPY package.json package-lock.json ./
RUN npm ci

# Copy source code and build production bundle
COPY . .
RUN npm run build
# Output: /app/dist — static HTML, JS, CSS files

# ── Stage 2: Serve with Nginx ─────────────────────────────────────────────────
FROM nginx:alpine

# Copy built static files from Stage 1 into Nginx's serving folder
COPY --from=builder /app/dist /usr/share/nginx/html

# SPA routing config: serve index.html for client-side routes (BrowserRouter)
COPY nginx.conf /etc/nginx/conf.d/default.conf

# Port 80 is Nginx's default
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
