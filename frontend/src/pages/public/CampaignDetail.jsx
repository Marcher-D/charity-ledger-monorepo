import { useState, useEffect } from 'react';
import { useParams } from 'react-router-dom';
import { apiRequest } from '../../services/api';
import toast from 'react-hot-toast';
// Import cái Modal Donate của ông anh vào đây (nếu để file riêng)
// import DonateModal from './DonateModal'; 

export default function CampaignDetail() {
  const { id } = useParams(); // [BỔ SUNG] Lấy ID chiến dịch từ thanh địa chỉ trình duyệt
  
  // [BỔ SUNG] Khởi tạo State chứa dữ liệu Backend
  const [campaign, setCampaign] = useState(null);
  const [donors, setDonors] = useState([]);
  const [loading, setLoading] = useState(true);

  // [BỔ SUNG] Hàm tải dữ liệu
  const fetchCampaignData = async () => {
    try {
      // Dùng Promise.all để gọi 2 API cùng lúc cho nhanh
      const [campaignData, donorsData] = await Promise.all([
        apiRequest(`/api/v1/campaigns/${id}`),
        apiRequest(`/api/v1/campaigns/${id}/donors`)
      ]);
      
      setCampaign(campaignData);
      setDonors(donorsData);
    } catch (err) {
      toast.error("Không thể tải thông tin chiến dịch!");
    } finally {
      setLoading(false);
    }
  };

  // [BỔ SUNG] Chạy hàm tải dữ liệu ngay khi vừa mở trang
  useEffect(() => {
    fetchCampaignData();
  }, [id]);

  if (loading) {
    return <div className="text-center py-20 text-xl font-bold">Đang tải dữ liệu từ Blockchain...</div>;
  }

  if (!campaign) {
    return <div className="text-center py-20 text-red-500">Chiến dịch không tồn tại!</div>;
  }

  // Tính phần trăm cho thanh tiến trình
  const progressPercent = Math.min((campaign.currentRaised / campaign.targetAmount) * 100, 100);

  return (
    <div className="max-w-7xl mx-auto px-4 py-8">
      {/* Header Info */}
      <div className="bg-white rounded-xl shadow-sm border border-gray-100 overflow-hidden mb-8">
        {/* Nút hiển thị ảnh thật từ Backend */}
        <div 
          className="h-64 bg-gray-200 bg-cover bg-center" 
          style={{ backgroundImage: `url(${campaign.coverImageUrl || 'https://via.placeholder.com/1200x400'})` }}
        ></div>
        
        <div className="p-8">
          <div className="flex justify-between items-start mb-4">
            <h1 className="text-3xl font-bold text-red-950">{campaign.title}</h1>
            <span className="px-3 py-1 text-sm font-bold rounded-full bg-blue-100 text-blue-800">
              {campaign.status}
            </span>
          </div>
          <p className="text-gray-600 mb-8 leading-relaxed">
            {campaign.description}
          </p>

          <div className="grid grid-cols-1 md:grid-cols-2 gap-12">
            {/* Progress Area */}
            <div>
              <div className="flex justify-between text-sm text-gray-500 mb-2">
                <span className="font-semibold text-gray-700">Progress</span>
                <span>{campaign.currentRaised} / {campaign.targetAmount} TCL</span>
              </div>
              <div className="w-full bg-gray-200 rounded-full h-3 mb-6">
                <div className="bg-brand-success h-3 rounded-full shadow-sm" style={{ width: `${progressPercent}%` }}></div>
              </div>
              <div className="text-sm text-gray-500 break-all bg-gray-50 p-3 rounded-md border border-gray-100">
                <strong className="text-gray-700 block mb-1">On-Chain Address:</strong>
                {campaign.onChainAddress || 'Pending deployment to ledger...'}
              </div>
            </div>

            {/* Donate Form - Có thể nhúng nguyên cái DonateModal.jsx của ông anh vào đây */}
            {/* <DonateModal campaignId={campaign.id} availableBalance={1000} onDonateSuccess={fetchCampaignData} /> */}
            <div className="bg-brand-surface p-6 rounded-xl border border-gray-200">
              <h3 className="text-lg font-semibold mb-4 text-brand-primary">Module Donate nằm ở đây</h3>
              <p className="text-sm text-gray-500">Ông anh thay khối div này bằng component DonateModal nhé!</p>
            </div>
          </div>
        </div>
      </div>

      {/* Donor List */}
      <h2 className="text-xl font-bold text-gray-900 mb-4">On-Chain Donors</h2>
      <div className="bg-white rounded-xl shadow-sm border border-gray-100 overflow-hidden">
        <table className="min-w-full divide-y divide-gray-200 text-sm">
          <thead className="bg-gray-50">
            <tr>
              <th className="px-6 py-3 text-left font-semibold text-gray-600">Donor</th>
              <th className="px-6 py-3 text-left font-semibold text-gray-600">Amount</th>
              <th className="px-6 py-3 text-left font-semibold text-gray-600">TxHash</th>
              <th className="px-6 py-3 text-left font-semibold text-gray-600">Status</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-200">
            {/* [BỔ SUNG] Map mảng donors lấy từ Backend ra */}
            {donors.length === 0 ? (
              <tr><td colSpan="4" className="px-6 py-4 text-center text-gray-500">Chưa có lượt quyên góp nào.</td></tr>
            ) : (
              donors.map((donor, index) => (
                <tr key={index} className="hover:bg-gray-50">
                  <td className="px-6 py-4 font-medium text-gray-900">{donor.username}</td>
                  <td className="px-6 py-4 text-brand-primary font-bold">{donor.amount} TCL</td>
                  <td className="px-6 py-4 text-gray-500 font-mono text-xs">
                    {donor.txHash || 'Pending...'}
                  </td>
                  <td className="px-6 py-4">
                    <span className={`px-2 py-1 rounded font-medium ${donor.status === 'SUCCESS' ? 'text-green-600 bg-green-50' : 'text-yellow-600 bg-yellow-50'}`}>
                      {donor.status}
                    </span>
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
}