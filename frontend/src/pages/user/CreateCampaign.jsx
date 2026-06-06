import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { toast } from 'react-hot-toast';
import { apiRequest } from '../../services/api'; 

export default function CreateCampaign() {
  const navigate = useNavigate();

  const [formData, setFormData] = useState({
    title: '',
    description: '',
    coverImageUrl: '',
    targetAmount: '' 
  });
  
  const [errors, setErrors] = useState({});
  const [isSubmitting, setIsSubmitting] = useState(false);

  const handleSubmit = async (e) => {
    e.preventDefault();
    setErrors({});
    setIsSubmitting(true);

    try {
      const payload = {
        ...formData,
        targetAmount: formData.targetAmount ? parseFloat(formData.targetAmount) : null
      };

      // [NÂNG CẤP]: Hứng cục CampaignResponse vào biến 'data'
      const data = await apiRequest('/api/v1/campaigns', {
        method: 'POST',
        body: JSON.stringify(payload)
      });

      // [NÂNG CẤP]: Tận dụng data trả về để báo cáo chi tiết cho người dùng
      toast.success(
        <div>
          <p className="font-bold">Đã tạo: {data.title}</p>
          <p className="text-sm">Trạng thái: {data.status}</p>
          <p className="text-xs text-gray-500 mt-1">Mã ID: {data.id.substring(0, 8)}...</p>
        </div>,
        { duration: 5000 } // Hiển thị thông báo trong 5 giây
      );

      // Đá về trang quản lý/dashboard
      navigate('/dashboard'); 

    } catch (err) {
      toast.error(err.message || 'Lỗi khi tạo chiến dịch!');
      if (err.data && err.data.validation) {
        setErrors(err.data.validation);
      }
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="max-w-3xl mx-auto px-4 py-12">
      <h1 className="text-3xl font-bold text-gray-900 mb-2">Create Campaign</h1>
      <p className="text-gray-500 mb-8">Submit a new charity campaign. It will be reviewed by administrators before being recorded on the ledger.</p>

      <form className="bg-emerald-600 p-8 rounded-xl shadow-sm border border-gray-100 space-y-6" onSubmit={handleSubmit}>
        
        <div>
          <label className="block text-sm font-bold text-gray-700 mb-1">Campaign Title</label>
          <input 
            type="text" 
            placeholder="e.g., Clean Water Fund" 
            value={formData.title} 
            onChange={(e) => setFormData({...formData, title: e.target.value})}
            className={`w-full px-4 py-2 border ${errors.title ? 'border-red-500' : 'border-gray-300'} rounded-md focus:ring-2 focus:ring-brand-success outline-none`} 
          />
          {errors.title && <p className="text-red-500 text-xs mt-1">{errors.title}</p>}
        </div>
        
        <div>
          <label className="block text-sm font-bold text-gray-700 mb-1">Target Amount (TCL)</label>
          <input 
            type="number" 
            step="0.01" 
            placeholder="5000.00" 
            value={formData.targetAmount}
            onChange={(e) => setFormData({...formData, targetAmount: e.target.value})}
            className={`w-full px-4 py-2 border ${errors.targetAmount ? 'border-red-500' : 'border-gray-300'} rounded-md focus:ring-2 focus:ring-brand-success outline-none`} 
          />
          {errors.targetAmount && <p className="text-red-500 text-xs mt-1">{errors.targetAmount}</p>}
        </div>

        <div>
          <label className="block text-sm font-bold text-gray-700 mb-1">Cover Image URL</label>
          <input 
            type="text" 
            placeholder="https://example.com/image.jpg" 
            value={formData.coverImageUrl}
            onChange={(e) => setFormData({...formData, coverImageUrl: e.target.value})}
            className={`w-full px-4 py-2 border ${errors.coverImageUrl ? 'border-red-500' : 'border-gray-300'} rounded-md focus:ring-2 focus:ring-brand-success outline-none`} 
          />
          {errors.coverImageUrl && <p className="text-red-500 text-xs mt-1">{errors.coverImageUrl}</p>}
        </div>

        <div>
          <label className="block text-sm font-bold text-gray-700 mb-1">Description</label>
          <textarea 
            rows="4" 
            placeholder="Detail the purpose of your campaign..." 
            value={formData.description}
            onChange={(e) => setFormData({...formData, description: e.target.value})}
            className={`w-full px-4 py-2 border ${errors.description ? 'border-red-500' : 'border-gray-300'} rounded-md focus:ring-2 focus:ring-brand-success outline-none`}
          ></textarea>
          {errors.description && <p className="text-red-500 text-xs mt-1">{errors.description}</p>}
        </div>

        <button 
          type="submit" 
          disabled={isSubmitting}
          className={`w-full py-3 text-white font-bold rounded-md transition-colors ${isSubmitting ? 'bg-gray-400 cursor-not-allowed border border-gray-400 p-4 rounded-md' : 'bg-brand-primary hover:bg-brand-accent border border-gray-400 p-4 rounded-md'}`}
        >
          {isSubmitting ? 'Submitting...' : 'Submit for Approval'}
        </button>
      </form>
    </div>
  );
}