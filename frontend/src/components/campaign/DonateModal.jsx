import React, { useState } from 'react';
import { toast } from 'react-hot-toast';
import { apiRequest } from '../../services/api'; // [BỔ SUNG] Import ống dẫn API

export default function DonateModal({ availableBalance = 0, campaignId }) {
  const [amount, setAmount] = useState('');
  const [note, setNote] = useState('');

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
      if (onDonateSuccess) onDonateSuccess(); // Gọi hàm load lại trang để cập nhật thanh tiến trình

    } catch (err) {
      toast.error(err.message || 'Lỗi khi quyên góp!');
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="bg-slate-900 border border-slate-800 p-6 rounded-2xl w-full max-w-sm shadow-xl">
      <h3 className="text-emerald-400 font-bold mb-4 flex items-center gap-2">
        <span>♥</span> Quyên góp
      </h3>
      
      <div className="mb-4 text-sm text-slate-400 flex justify-between">
        <span>Khả dụng:</span>
        <span className="text-emerald-400 font-bold">${availableBalance.toFixed(2)}</span>
      </div>

      <form onSubmit={handleDonate}>
        {/* Ô nhập số tiền (Chỉ nhận số, cho phép 2 số thập phân, không vượt quá số dư) */}
        <div className="mb-3">
          <input 
            type="number" 
            step="0.01" 
            min="0.01"
            max={availableBalance}
            value={amount}
            onChange={(e) => setAmount(e.target.value)}
            placeholder="Nhập số tiền (VD: 10.50)" 
            className="w-full bg-slate-950 border border-slate-800 p-3 rounded-xl outline-none focus:border-emerald-500 transition text-sm text-slate-200"
            required
          />
        </div>

        {/* Ô lời nhắn */}
        <div className="mb-4">
          <input 
            type="text" 
            value={note}
            onChange={(e) => setNote(e.target.value)}
            placeholder="Lời nhắn ủng hộ (Tùy chọn)..." 
            className="w-full bg-slate-950 border border-slate-800 p-3 rounded-xl outline-none focus:border-emerald-500 transition text-sm text-slate-200"
          />
        </div>

        <button 
          type="submit"
          disabled={!amount || parseFloat(amount) > availableBalance}
          className="w-full bg-emerald-500 text-slate-950 font-bold py-3 rounded-xl hover:bg-emerald-400 transition shadow-lg shadow-emerald-500/20 disabled:opacity-50 disabled:cursor-not-allowed"
        >
          XÁC NHẬN CHUYỂN
        </button>
      </form>
    </div>
  );
}