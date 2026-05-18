export default function CampaignDetail() {
  return (
    <div className="max-w-7xl mx-auto px-4 py-8">
      {/* Header Info */}
      <div className="bg-white rounded-xl shadow-sm border border-gray-100 overflow-hidden mb-8">
        <div className="h-64 bg-gray-200 animate-pulse"></div> {/* Placeholder Cover */}
        <div className="p-8">
          <div className="flex justify-between items-start mb-4">
            <h1 className="text-3xl font-bold text-red-950">School Supplies for Remote Villages</h1>
            <span className="px-3 py-1 text-sm font-bold rounded-full bg-blue-100 text-blue-800">OPEN</span>
          </div>
          <p className="text-gray-600 mb-8 leading-relaxed">
            Help us buy essential school supplies for children in remote areas. Every transaction is recorded immutably on the blockchain to ensure 100% transparency.
          </p>

          <div className="grid grid-cols-1 md:grid-cols-2 gap-12">
            {/* Progress Area */}
            <div>
              <div className="flex justify-between text-sm text-gray-500 mb-2">
                <span className="font-semibold text-gray-700">Progress</span>
                <span>125.50 / 1000.00 TCL</span>
              </div>
              <div className="w-full bg-gray-200 rounded-full h-3 mb-6">
                <div className="bg-brand-success h-3 rounded-full shadow-sm" style={{ width: '12.5%' }}></div>
              </div>
              <div className="text-sm text-gray-500 break-all bg-gray-50 p-3 rounded-md border border-gray-100">
                <strong className="text-gray-700 block mb-1">On-Chain Address:</strong>
                0xabc123...def456
              </div>
            </div>

            {/* Donate Form */}
            <div className="bg-brand-surface p-6 rounded-xl border border-gray-200">
              <h3 className="text-lg font-semibold mb-4 text-brand-primary">Make a Donation</h3>
              <div className="flex space-x-3 mb-4">
                <input type="number" placeholder="Amount (TCL)" className="flex-grow px-4 py-2 border border-gray-300 rounded-md focus:ring-2 focus:ring-brand-success outline-none" />
                <button className="px-6 py-2 bg-brand-success text-brand-dark font-bold rounded-md hover:opacity-90 transition-opacity shadow-sm">
                  Donate
                </button>
              </div>
              <input type="text" placeholder="Add a note (optional)" className="w-full px-4 py-2 border border-gray-300 rounded-md text-sm outline-none focus:ring-2 focus:ring-brand-success" />
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
            <tr className="hover:bg-gray-50">
              <td className="px-6 py-4 font-medium text-gray-900">alice</td>
              <td className="px-6 py-4 text-brand-primary font-bold">25.00 TCL</td>
              <td className="px-6 py-4 text-gray-500 font-mono">0xabc...123</td>
              <td className="px-6 py-4">
                <span className="text-green-600 bg-green-50 px-2 py-1 rounded font-medium">SUCCESS</span>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  );
}