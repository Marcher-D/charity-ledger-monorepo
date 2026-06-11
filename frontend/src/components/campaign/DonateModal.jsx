import { useState } from 'react';
import { toast } from 'react-hot-toast';
import { apiRequest } from '../../services/api'; // [BỔ SUNG] Import ống dẫn API
import { useWallet } from '../../context/WalletContext';

export default function DonateModal({ availableBalance = 0, campaignId, onDonateSuccess }) {
  const { refreshWallet } = useWallet();
  const [amount, setAmount] = useState('');
  const [note, setNote] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const numericAvailableBalance = Number(availableBalance) || 0;

  const handleDonate = async (e) => {
    e.preventDefault();
    setIsSubmitting(true);
    
    try {
      // [BỔ SUNG] Khớp với TransactionController (hoặc form DonateRequest của Backend)
      await apiRequest('/api/v1/transactions/donate', {
        method: 'POST',
        body: JSON.stringify({
          campaignId: campaignId,
          amount: parseFloat(amount),
          note: note
        })
      });

      toast.success("Quyên góp thành công! Giao dịch đang chờ mạng PPoS xác thực.");
      setAmount('');
      setNote('');
      refreshWallet(); // Số dư bị khóa escrow ngay — kéo số mới về liền
      if (onDonateSuccess) onDonateSuccess(); // Gọi hàm load lại trang để cập nhật thanh tiến trình

    } catch (err) {
      toast.error(err.message || 'Lỗi khi quyên góp!');
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="bg-brand-panel border border-brand-line p-6 rounded-2xl w-full max-w-sm shadow-xl">
      <h3 className="text-emerald-400 font-bold mb-4 flex items-center gap-2">
        <span>♥</span> DONATE
      </h3>
      
      <div className="mb-4 text-sm text-brand-creamMuted flex justify-between">
        <span>Available:</span>
        <span className="text-emerald-400 font-bold">${numericAvailableBalance.toFixed(2)}</span>
      </div>

      <form onSubmit={handleDonate}>
        {/* Ô nhập số tiền (Chỉ nhận số, cho phép 2 số thập phân, không vượt quá số dư) */}
        <div className="mb-3">
          <input 
            type="number" 
            step="0.01" 
            min="0.01"
            max={numericAvailableBalance}
            value={amount}
            onChange={(e) => setAmount(e.target.value)}
            placeholder="Choose an amount (E.G: 10.50)" 
            className="w-full bg-brand-dark border border-brand-line p-3 rounded-xl outline-none focus:border-emerald-500 transition text-sm text-brand-cream"
            required
          />
        </div>

        {/* Ô lời nhắn */}
        <div className="mb-4">
          <input 
            type="text" 
            value={note}
            onChange={(e) => setNote(e.target.value)}
            placeholder="Give some note (Optional)..." 
            className="w-full bg-brand-dark border border-brand-line p-3 rounded-xl outline-none focus:border-emerald-500 transition text-sm text-brand-cream font-copy"
          />
        </div>

        <button 
          type="submit"
          disabled={isSubmitting || !amount || parseFloat(amount) > numericAvailableBalance}
          className="w-full bg-brand-success text-brand-dark font-bold py-3 rounded-xl hover:bg-brand-accent transition shadow-lg shadow-emerald-500/20 disabled:opacity-50 disabled:cursor-not-allowed"
        >
          {isSubmitting ? 'PROCESSING...' : 'CONFIRM TO SEND'}
        </button>
      </form>
    </div>
  );
}
