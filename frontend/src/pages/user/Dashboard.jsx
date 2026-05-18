export default function Dashboard() {
  return (
    <div className="max-w-7xl mx-auto px-4 py-8">
      <h1 className="text-2xl font-bold text-gray-900 mb-6">User Dashboard</h1>
      
      <div className="grid grid-cols-1 md:grid-cols-3 gap-6 mb-8">
        {/* Wallet Info */}
        <div className="bg-white p-6 rounded-xl shadow-sm border border-gray-100 col-span-2">
          <h2 className="text-lg font-semibold mb-4 text-gray-700">Wallet Overview</h2>
          <div className="grid grid-cols-3 gap-4 text-center">
            <div className="p-4 bg-gray-50 rounded-lg">
              <p className="text-sm text-gray-500">Available</p>
              <p className="text-xl font-bold text-brand-primary">100.00</p>
            </div>
            <div className="p-4 bg-gray-50 rounded-lg">
              <p className="text-sm text-gray-500">Pending</p>
              <p className="text-xl font-bold text-brand-warning">0.00</p>
            </div>
            <div className="p-4 bg-gray-50 rounded-lg">
              <p className="text-sm text-gray-500">Staked (PPoS)</p>
              <p className="text-xl font-bold text-brand-success">0.00</p>
            </div>
          </div>
          <div className="mt-6 flex space-x-4">
            <button className="px-4 py-2 bg-brand-primary text-white rounded-md font-medium">Stake</button>
            <button className="px-4 py-2 border border-gray-300 rounded-md font-medium hover:bg-gray-50">Unstake</button>
          </div>
        </div>

        {/* Validator Status */}
        <div className="bg-white p-6 rounded-xl shadow-sm border border-gray-100 flex flex-col justify-center items-center text-center">
          <h2 className="text-lg font-semibold mb-2 text-gray-700">Validator Status</h2>
          <div className="w-16 h-16 bg-gray-200 rounded-full mb-4 flex items-center justify-center text-gray-500 font-bold">Off</div>
          <p className="text-sm text-gray-500 mb-4">Opt-in to participate in network consensus.</p>
          <button className="px-4 py-2 border border-brand-success text-brand-success font-medium rounded-md w-full hover:bg-brand-success hover:text-white transition-colors">
            Toggle Validator
          </button>
        </div>
      </div>

      {/* Transaction History Placeholder */}
      <h2 className="text-lg font-semibold mb-4 text-gray-700">Recent Transactions</h2>
      <div className="bg-white rounded-xl shadow-sm border border-gray-100 p-6">
        <p className="text-gray-500 text-center py-4">Loading transactions...</p>
      </div>
    </div>
  );
}