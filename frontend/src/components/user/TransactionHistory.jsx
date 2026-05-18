import React from 'react';

export default function TransactionHistory({ transactions = [] }) {
  // Hàm phụ trợ map màu cho trạng thái (Không làm thay đổi logic gốc)
  const getStatusColor = (status) => {
    if (status === 'SUCCESS') return 'text-emerald-400 bg-emerald-400/10';
    if (status === 'FAILED') return 'text-red-400 bg-red-400/10';
    return 'text-yellow-400 bg-yellow-400/10'; // PENDING
  };

  return (
    <div className="bg-slate-900 border border-slate-800 p-6 rounded-2xl w-full mt-6">
      <h3 className="text-lg font-bold text-slate-200 mb-4">Lịch sử giao dịch</h3>
      
      {transactions.length === 0 ? (
        <p className="text-sm text-slate-400">Chưa có giao dịch nào.</p>
      ) : (
        <div className="space-y-3">
          {transactions.map((tx) => (
            <div key={tx.id} className="flex items-center justify-between p-3 bg-slate-950 border border-slate-800 rounded-xl">
              <div>
                <p className="text-sm font-bold text-slate-200">{tx.type} <span className="text-slate-500 font-normal ml-2">{new Date(tx.createdAt).toLocaleDateString()}</span></p>
                {tx.txHash && <p className="text-xs text-slate-500 mt-1">Hash: {tx.txHash.substring(0, 10)}...</p>}
              </div>
              <div className="text-right">
                <p className="text-sm font-bold text-slate-200 mb-1">${tx.amount.toFixed(2)}</p>
                <span className={`text-[10px] px-2 py-0.5 rounded font-bold ${getStatusColor(tx.status)}`}>
                  {tx.status}
                </span>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}