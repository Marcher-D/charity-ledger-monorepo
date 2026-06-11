import { useState, useCallback, useEffect } from 'react';
import { useParams } from 'react-router-dom';
import { apiRequest } from '../../services/api';
import toast from 'react-hot-toast';
import DonateModal from '../../components/campaign/DonateModal';
import { isAdminRole } from '../../services/auth';
import { useWallet } from '../../context/WalletContext';

export default function CampaignDetail() {
  const { id } = useParams(); // [BỔ SUNG] Lấy ID chiến dịch từ thanh địa chỉ trình duyệt

  // [BỔ SUNG] Khởi tạo State chứa dữ liệu Backend
  const [campaign, setCampaign] = useState(null);
  const [donors, setDonors] = useState([]);
  // Số dư ví từ WalletContext: nạp tiền ở Dashboard xong quay lại đây là
  // form quyên góp thấy số mới trong ≤5 giây, không cần F5 (null nếu Admin
  // hoặc chưa đăng nhập).
  const { profile } = useWallet();
  const [loading, setLoading] = useState(true);

  // [BỔ SUNG] Hàm tải dữ liệu
  const fetchCampaignData = useCallback(async () => {
    try {
      // Dùng Promise.all để gọi 2 API cùng lúc cho nhanh
      const [campaignData, donorsData] = await Promise.all([
        apiRequest(`/api/v1/campaigns/${id}`),
        apiRequest(`/api/v1/campaigns/${id}/donors`)
      ]);

      setCampaign(campaignData);
      setDonors(donorsData);
    } catch (err) {
      console.error("Data loading error:", err);
      toast.error("Cannot load campaign information!");
    } finally {
      setLoading(false);
    }
  }, [id]);

  // [BỔ SUNG] Chạy hàm tải dữ liệu ngay khi vừa mở trang
  useEffect(() => {
    fetchCampaignData();
  }, [fetchCampaignData]);

  // Auto-refresh while any donation is still waiting for on-chain
  // confirmation, so the progress bar and donor statuses update to
  // SUCCESS/FAILED without a manual reload (consensus takes ~10-30s).
  useEffect(() => {
    const hasPending = donors.some((donor) => donor.status === 'PENDING');
    if (!hasPending) return undefined;

    const intervalId = setInterval(fetchCampaignData, 5000);
    return () => clearInterval(intervalId);
  }, [donors, fetchCampaignData]);

  if (loading) {
    return <div className="text-center py-20 text-xl font-bold">Loading data from Blockchain...</div>;
  }

  if (!campaign) {
    return <div className="text-center py-20 text-red-500">Campaign does not exist!</div>;
  }

  // Tính phần trăm cho thanh tiến trình
  const progressPercent = Math.min((campaign.currentRaised / campaign.targetAmount) * 100, 100);
  const canDonate = campaign.status === 'OPEN' && profile && !isAdminRole(profile.role);

  return (
    <div className="max-w-7xl mx-auto px-4 py-8 text-brand-cream">
      {/* Header Info */}
      <div className="bg-brand-card rounded-xl shadow-2xl border border-brand-line overflow-hidden mb-8">
        {/* Nút hiển thị ảnh thật từ Backend */}
        <div 
          className="h-64 bg-brand-panel bg-cover bg-center" 
          style={{ backgroundImage: `url(${campaign.coverImageUrl || 'https://via.placeholder.com/1200x400'})` }}
        ></div>
        
        <div className="p-5 sm:p-8">
          <div className="flex flex-wrap justify-between items-start gap-2 mb-4">
            <h1 className="text-2xl sm:text-3xl font-bold text-brand-cream">{campaign.title}</h1>
            <span className="px-3 py-1 text-sm font-bold rounded-full bg-emerald-500/10 text-emerald-400 border border-emerald-500/30">
              {campaign.status}
            </span>
          </div>
          <p className="font-copy text-brand-creamMuted text-lg mb-8 leading-relaxed text-left">
            {campaign.description}
          </p>

          <div className="grid grid-cols-1 md:grid-cols-2 gap-6 md:gap-12">
            {/* Progress Area */}
            <div>
              <div className="flex justify-between text-sm text-brand-creamMuted mb-2">
                <span className="font-semibold text-brand-cream">Progress</span>
                <span>{campaign.currentRaised} / {campaign.targetAmount} TCL</span>
              </div>
              <div className="w-full bg-brand-dark rounded-full h-3 mb-6 border border-brand-line overflow-hidden">
                <div className="bg-brand-success h-3 rounded-full shadow-sm" style={{ width: `${progressPercent}%` }}></div>
              </div>
              <div className="text-sm text-brand-creamMuted break-all bg-brand-dark/70 p-3 rounded-md border border-brand-line">
                <strong className="text-brand-cream block mb-1">On-Chain Address:</strong>
                {campaign.onChainAddress || 'Pending deployment to ledger...'}
              </div>
            </div>

            {canDonate ? (
              <DonateModal campaignId={campaign.id} availableBalance={profile.availableBalance} onDonateSuccess={fetchCampaignData} />
            ) : (
              <div className="bg-brand-panel p-6 rounded-xl border border-brand-line">
                <h3 className="text-lg font-semibold mb-4 text-brand-success">Donate</h3>
                <p className="font-copy text-sm text-brand-creamMuted leading-relaxed">
                  {profile && isAdminRole(profile.role)
                    ? 'Admin does not have wallet. Use Admin Panel to allow pending campaigns.'
                    : 'Log in with user account to donate for this campaign.'}
                </p>
              </div>
            )}
          </div>
        </div>
      </div>

      {/* Donor List */}
      <h2 className="text-xl font-bold text-brand-cream mb-4">On-Chain Donors</h2>
      <div className="bg-brand-card rounded-xl shadow-sm border border-brand-line overflow-hidden">
        {/* Mobile: bảng 4 cột rộng hơn màn hình điện thoại → cuộn ngang trong khung */}
        <div className="overflow-x-auto">
        <table className="min-w-full divide-y divide-brand-line text-sm">
          <thead className="bg-brand-panel">
            <tr>
              <th className="px-6 py-3 text-left font-semibold text-brand-creamMuted">Donor</th>
              <th className="px-6 py-3 text-left font-semibold text-brand-creamMuted">Amount</th>
              <th className="px-6 py-3 text-left font-semibold text-brand-creamMuted">TxHash</th>
              <th className="px-6 py-3 text-left font-semibold text-brand-creamMuted">Status</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-brand-line">
            {/* [BỔ SUNG] Map mảng donors lấy từ Backend ra */}
            {donors.length === 0 ? (
              <tr><td colSpan="4" className="px-6 py-4 text-center text-brand-creamMuted">Chưa có lượt quyên góp nào.</td></tr>
            ) : (
              donors.map((donor, index) => (
                <tr key={index} className="hover:bg-brand-panel/70">
                  <td className="px-6 py-4 font-medium text-brand-cream">{donor.username}</td>
                  <td className="px-6 py-4 text-brand-success font-bold">{donor.amount} TCL</td>
                  <td className="px-6 py-4 text-brand-creamMuted font-mono text-xs">
                    {donor.txHash || 'Pending...'}
                  </td>
                  <td className="px-6 py-4">
                    <span className={`px-2 py-1 rounded font-medium ${donor.status === 'SUCCESS' ? 'text-brand-success bg-brand-success/10 border border-brand-success/20' : 'text-brand-warning bg-brand-warning/10 border border-brand-warning/20'}`}>
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
    </div>
  );
}
