import React from 'react';
import { Link } from 'react-router-dom';

// Component nhận vào 1 object campaign chứa dữ liệu từ API
export default function CampaignCard({ campaign }) {
  return (
    <Link 
      to={`/campaign/${campaign.id}`} 
      className="block bg-slate-900 border border-slate-800 p-5 rounded-2xl hover:border-emerald-500 hover:shadow-lg hover:shadow-emerald-500/10 transition group"
    >
      <div className="flex justify-between items-start mb-3">
        {/* Tên chiến dịch */}
        <h3 className="text-xl font-bold text-slate-200 group-hover:text-emerald-400 transition">
          {campaign.title}
        </h3>
        {/* Trạng thái */}
        <span className="text-xs px-2 py-1 bg-slate-800 text-slate-300 font-medium rounded uppercase tracking-wider">
          {campaign.status}
        </span>
      </div>
      
      {/* Thông tin tiền tệ */}
      <div className="text-sm text-slate-400">
        Đã góp: <span className="text-emerald-400 font-bold">${campaign.currentRaised.toFixed(2)}</span> / ${campaign.targetAmount.toFixed(2)}
      </div>
      
      {/* Thanh tiến trình mini (Thêm vào cho trực quan, không đổi data logic) */}
      <div className="w-full bg-slate-950 h-2 rounded-full mt-3 overflow-hidden">
        <div 
          className="bg-emerald-500 h-full" 
          style={{ width: `${Math.min((campaign.currentRaised / campaign.targetAmount) * 100, 100)}%` }}
        ></div>
      </div>
    </Link>
  );
}