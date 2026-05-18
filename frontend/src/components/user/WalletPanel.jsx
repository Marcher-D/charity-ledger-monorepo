import React from 'react';

export default function WalletPanel({ profile }) {
  if (!profile) return <div>Đang tải ví...</div>;

  return (
    <div className="bg-slate-900 border border-slate-800 p-6 rounded-2xl w-full">
      <h3 className="text-lg font-bold text-slate-200 mb-4">Tài sản PPoS</h3>
      
      <div className="grid grid-cols-3 gap-4 mb-6">
        <div className="bg-slate-950 p-4 rounded-xl border border-slate-800">
          <p className="text-xs text-slate-400 mb-1">Khả dụng (Available)</p>
          <p className="text-xl text-emerald-400 font-bold">${profile.availableBalance.toFixed(2)}</p>
        </div>
        <div className="bg-slate-950 p-4 rounded-xl border border-slate-800">
          <p className="text-xs text-slate-400 mb-1">Đang chờ (Pending)</p>
          <p className="text-xl text-yellow-400 font-bold">${profile.pendingBalance.toFixed(2)}</p>
        </div>
        <div className="bg-slate-950 p-4 rounded-xl border border-slate-800">
          <p className="text-xs text-slate-400 mb-1">Đã khóa (Staked)</p>
          <p className="text-xl text-blue-400 font-bold">${profile.stakedBalance.toFixed(2)}</p>
        </div>
      </div>

      {/* Các nút gọi API Stake, Unstake, Toggle Validator sẽ gắn hàm vào đây */}
      <div className="flex gap-2">
        <button className="px-4 py-2 bg-emerald-500/10 text-emerald-400 border border-emerald-500/50 rounded-lg text-sm font-bold hover:bg-emerald-500/20">
          Stake Tiền
        </button>
        <button className="px-4 py-2 bg-slate-800 text-slate-300 border border-slate-700 rounded-lg text-sm hover:bg-slate-700">
          Unstake
        </button>
        <button className={`px-4 py-2 rounded-lg text-sm ml-auto border ${profile.activeValidator ? 'bg-red-500/10 text-red-500 border-red-500/50' : 'bg-blue-500/10 text-blue-400 border-blue-500/50'}`}>
          {profile.activeValidator ? 'Rời ghế Validator' : 'Đăng ký Validator'}
        </button>
      </div>
    </div>
  );
}