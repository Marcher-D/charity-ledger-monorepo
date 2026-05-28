// [SỬA]: Nhận thêm prop isToggling và onToggleValidator
export default function WalletPanel({ profile, isToggling, onToggleValidator }) {
  if (!profile) return <div>Đang tải ví...</div>;

  return (
    <div className="bg-slate-900 border border-slate-800 p-6 rounded-2xl w-full">
      {/* ... (Giữ nguyên phần render số dư ở giữa) ... */}

      <div className="flex gap-2">
        <button className="px-4 py-2 bg-emerald-500/10 text-emerald-400 border border-emerald-500/50 rounded-lg text-sm font-bold hover:bg-emerald-500/20">
          Stake Tiền
        </button>
        <button className="px-4 py-2 bg-slate-800 text-slate-300 border border-slate-700 rounded-lg text-sm hover:bg-slate-700">
          Unstake
        </button>
        
        {/* [SỬA]: Gắn hàm onToggleValidator vào nút */}
        <button 
          onClick={onToggleValidator}
          disabled={isToggling}
          className={`px-4 py-2 rounded-lg text-sm ml-auto border transition-colors ${
            profile.activeValidator 
              ? 'bg-red-500/10 text-red-500 border-red-500/50 hover:bg-red-500 hover:text-white' 
              : 'bg-blue-500/10 text-blue-400 border-blue-500/50 hover:bg-blue-500 hover:text-white'
          } ${isToggling ? 'opacity-50 cursor-not-allowed' : ''}`}
        >
          {isToggling ? 'Đang xử lý...' : (profile.activeValidator ? 'Rời ghế Validator' : 'Đăng ký Validator')}
        </button>
      </div>
    </div>
  );
}