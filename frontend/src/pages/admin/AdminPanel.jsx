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
          toast.error("Truy cập bị từ chối! Bạn không có quyền Admin.");
        } else {
          toast.error(err.message || "Không thể tải danh sách duyệt.");
        }
      }
    };
    fetchPending();
  }, []);

  // [BỔ SUNG] Hàm xử lý Duyệt/Từ chối (Bắt lỗi 409 Conflict)
  const handleAction = async (id, action) => {
    try {
      await apiRequest(`/api/v1/admin/campaigns/${id}/${action}`, { method: 'PUT' });
      toast.success(`Đã gửi lệnh ${action} lên mạng lưới!`);
      // Cập nhật lại list sau khi xử lý thành công
      setCampaigns(prev => prev.filter(camp => camp.id !== id));
    } catch (err) {
      if (err.status === 403) {
        toast.error("Bạn không có quyền thực hiện hành động này!");
      } else if (err.status === 409) {
        // [BỔ SUNG] Bắt lỗi 409 Conflict theo yêu cầu
        toast.error("Xung đột dữ liệu! Chiến dịch này có thể đã được xử lý.");
      } else {
        toast.error(err.message || `Lỗi khi xử lý ${action}.`);
      }
    }
  };

  return (
    <div className="max-w-7xl mx-auto px-4 py-8">
      <h1 className="text-2xl font-bold text-gray-900 mb-6">Admin Control Panel</h1>
      
      <div className="bg-white rounded-xl shadow-sm border border-gray-100 overflow-hidden">
        <div className="px-6 py-4 border-b border-gray-100 bg-gray-50 flex justify-between items-center">
          <h2 className="font-semibold text-gray-700">Pending Campaigns</h2>
          <span className="text-xs font-bold bg-brand-warning text-white px-2 py-1 rounded">
            {campaigns.length} Require Action {/* [BỔ SUNG] Render số lượng động */}
          </span>
        </div>
        
        <table className="min-w-full divide-y divide-gray-200 text-sm">
          <thead className="bg-white">
            <tr>
              <th className="px-6 py-3 text-left font-semibold text-gray-600">Title</th>
              <th className="px-6 py-3 text-left font-semibold text-gray-600">Target</th>
              <th className="px-6 py-3 text-left font-semibold text-gray-600">Date</th>
              <th className="px-6 py-3 text-right font-semibold text-gray-600">Actions</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-200">
            {/* [BỔ SUNG] Render list động dựa trên UI cũ, vẫn giữ nguyên class HTML */}
            {campaigns.length > 0 ? campaigns.map((camp) => (
              <tr key={camp.id} className="hover:bg-gray-50">
                <td className="px-6 py-4 font-medium text-gray-900">{camp.title}</td>
                <td className="px-6 py-4 text-gray-700">{camp.targetAmount}</td>
                <td className="px-6 py-4 text-gray-500">{new Date(camp.createdAt).toISOString().split('T')[0]}</td>
                <td className="px-6 py-4 text-right space-x-3">
                  {/* [BỔ SUNG] Gắn hàm click */}
                  <button onClick={() => handleAction(camp.id, 'approve')} className="text-green-600 font-semibold hover:text-green-800">Approve</button>
                  <button onClick={() => handleAction(camp.id, 'reject')} className="text-red-600 font-semibold hover:text-red-800">Reject</button>
                </td>
              </tr>
            )) : (
              // [BỔ SUNG] Dòng mặc định khi không có data (Giữ nguyên form UI gốc bạn đưa)
              <tr className="hover:bg-gray-50">
                <td className="px-6 py-4 font-medium text-gray-900">Medical Aid Fund</td>
                <td className="px-6 py-4 text-gray-700">2000.00</td>
                <td className="px-6 py-4 text-gray-500">2026-05-16</td>
                <td className="px-6 py-4 text-right space-x-3">
                  <button className="text-green-600 font-semibold hover:text-green-800">Approve</button>
                  <button className="text-red-600 font-semibold hover:text-red-800">Reject</button>
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
}