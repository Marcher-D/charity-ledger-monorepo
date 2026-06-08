// [BỔ SUNG] Import thư viện
import { useState, useEffect } from 'react';
import { toast } from 'react-hot-toast';
import { apiRequest } from '../../services/api';

export default function AdminPanel() {
  // [BỔ SUNG] State quản lý danh sách
  const [campaigns, setCampaigns] = useState([]);

  // [BỔ SUNG] Gọi API lấy danh sách khi load trang (Bắt lỗi 403)
  useEffect(() => {
    const fetchPending = async () => {
      try {
        const data = await apiRequest('/api/v1/campaigns?status=PENDING');
        setCampaigns(data.items || []);
      } catch (err) {
        if (err.status === 403) {
          toast.error("Access denied! You have no admin rights.");
        } else {
          toast.error(err.message || "Cannot load pending campaign list.");
        }
      }
    };
    fetchPending();
  }, []);

  // [BỔ SUNG] Hàm xử lý Duyệt/Từ chối (Bắt lỗi 409 Conflict)
  const handleAction = async (id, action) => {
    try {
      await apiRequest(`/api/v1/admin/campaigns/${id}/${action}`, { method: 'PUT' });
      toast.success(`Send ${action} to network!`);
      // Cập nhật lại list sau khi xử lý thành công
      setCampaigns(prev => prev.filter(camp => camp.id !== id));
    } catch (err) {
      if (err.status === 403) {
        toast.error("You have no rights to make this action!");
      } else if (err.status === 409) {
        // [BỔ SUNG] Bắt lỗi 409 Conflict theo yêu cầu
        toast.error("This campaign maybe processed yet.");
      } else {
        toast.error(err.message || `Error processing ${action}.`);
      }
    }
  };

  return (
    <div className="max-w-7xl mx-auto px-4 py-8 text-brand-cream">
      <h1 className="text-2xl font-bold text-brand-cream mb-6">Admin Control Panel</h1>
      
      <div className="bg-brand-card rounded-xl shadow-2xl border border-brand-line overflow-hidden">
        <div className="px-6 py-4 border-b border-brand-line bg-brand-panel flex justify-between items-center">
          <h2 className="font-semibold text-brand-cream">Pending Campaigns</h2>
          <span className="text-xs font-bold bg-brand-warning text-brand-dark px-2 py-1 rounded">
            {campaigns.length} Require Action {/* [BỔ SUNG] Render số lượng động */}
          </span>
        </div>
        
        <table className="min-w-full divide-y divide-brand-line text-sm">
          <thead className="bg-brand-card">
            <tr>
              <th className="px-6 py-3 text-left font-semibold text-brand-creamMuted">Title</th>
              <th className="px-6 py-3 text-left font-semibold text-brand-creamMuted">Target</th>
              <th className="px-6 py-3 text-left font-semibold text-brand-creamMuted">Date</th>
              <th className="px-6 py-3 text-right font-semibold text-brand-creamMuted">Actions</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-brand-line">
            {/* [BỔ SUNG] Render list động dựa trên UI cũ, vẫn giữ nguyên class HTML */}
            {campaigns.length > 0 ? campaigns.map((camp) => (
              <tr key={camp.id} className="hover:bg-brand-panel/70">
                <td className="px-6 py-4 font-medium text-brand-cream">{camp.title}</td>
                <td className="px-6 py-4 text-brand-creamMuted">{camp.targetAmount}</td>
                <td className="px-6 py-4 text-brand-creamMuted">{new Date(camp.createdAt).toISOString().split('T')[0]}</td>
                <td className="px-6 py-4 text-right space-x-3">
                  {/* [BỔ SUNG] Gắn hàm click */}
                  <button onClick={() => handleAction(camp.id, 'approve')} className="text-green-600 font-semibold hover:text-green-800">Approve</button>
                  <button onClick={() => handleAction(camp.id, 'reject')} className="text-red-600 font-semibold hover:text-red-800">Reject</button>
                </td>
              </tr>
            )) : (
              <tr>
                <td colSpan="4" className="px-6 py-8 text-center text-brand-creamMuted">
                  No pending campaigns.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
}
