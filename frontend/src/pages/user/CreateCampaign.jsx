export default function CreateCampaign() {
  return (
    <div className="max-w-3xl mx-auto px-4 py-12">
      <h1 className="text-3xl font-bold text-gray-900 mb-2">Create Campaign</h1>
      <p className="text-gray-500 mb-8">Submit a new charity campaign. It will be reviewed by administrators before being recorded on the ledger.</p>

      <form className="bg-white p-8 rounded-xl shadow-sm border border-gray-100 space-y-6" onSubmit={(e) => e.preventDefault()}>
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">Campaign Title</label>
          <input type="text" placeholder="e.g., Clean Water Fund" className="w-full px-4 py-2 border border-gray-300 rounded-md focus:ring-2 focus:ring-brand-success outline-none" />
        </div>
        
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">Target Amount (TCL)</label>
          <input type="number" placeholder="5000.00" className="w-full px-4 py-2 border border-gray-300 rounded-md focus:ring-2 focus:ring-brand-success outline-none" />
        </div>

        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">Cover Image URL</label>
          <input type="text" placeholder="https://example.com/image.jpg" className="w-full px-4 py-2 border border-gray-300 rounded-md focus:ring-2 focus:ring-brand-success outline-none" />
        </div>

        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">Description</label>
          <textarea rows="4" placeholder="Detail the purpose of your campaign..." className="w-full px-4 py-2 border border-gray-300 rounded-md focus:ring-2 focus:ring-brand-success outline-none"></textarea>
        </div>

        <button className="w-full py-3 bg-brand-primary text-white font-medium rounded-md hover:bg-brand-accent transition-colors">
          Submit for Approval
        </button>
      </form>
    </div>
  );
}