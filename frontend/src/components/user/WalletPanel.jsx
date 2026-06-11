import { useState } from 'react';

export default function WalletPanel({
  profile,
  isStaking,
  isUnstaking,
  onStake,
  onUnstake,
  isDepositing,
  isWithdrawing,
  onDeposit,
  onWithdraw
}) {
  const [amount, setAmount] = useState('');

  if (!profile) return <div className="text-brand-creamMuted">Loading wallet...</div>;

  const availableBalance = Number(profile.availableBalance) || 0;
  const pendingBalance = Number(profile.pendingBalance) || 0;
  const stakedBalance = Number(profile.stakedBalance) || 0;
  const requestedAmount = Number(amount);
  const hasValidAmount = Number.isFinite(requestedAmount) && requestedAmount > 0;
  const actionInProgress = isStaking || isUnstaking || isDepositing || isWithdrawing;
  const canDeposit = hasValidAmount && !actionInProgress;
  const canWithdraw = hasValidAmount && requestedAmount <= availableBalance && !actionInProgress;
  const canStake = hasValidAmount && requestedAmount <= availableBalance && !actionInProgress;
  // Validator participation is automatic on-chain (any staked node becomes a
  // validator once its stake enters the sortition lookback) — no manual toggle.
  const canUnstake = hasValidAmount && requestedAmount <= stakedBalance && !actionInProgress;

  const handleDeposit = async () => {
    const success = await onDeposit(amount);
    if (success) setAmount('');
  };

  const handleWithdraw = async () => {
    const success = await onWithdraw(amount);
    if (success) setAmount('');
  };

  const handleStake = async () => {
    const success = await onStake(amount);
    if (success) setAmount('');
  };

  const handleUnstake = async () => {
    const success = await onUnstake(amount);
    if (success) setAmount('');
  };

  return (
    <div className="bg-brand-card border border-brand-line p-6 rounded-2xl w-full">
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-3 mb-5">
        <div className="bg-brand-dark/60 border border-brand-line rounded-lg px-4 py-3">
          <p className="text-xs text-brand-creamMuted">Available</p>
          <p className="text-lg font-bold text-brand-success">{availableBalance.toFixed(2)} TCL</p>
        </div>
        <div className="bg-brand-dark/60 border border-brand-line rounded-lg px-4 py-3">
          <p className="text-xs text-brand-creamMuted">Pending</p>
          <p className="text-lg font-bold text-brand-cream">{pendingBalance.toFixed(2)} TCL</p>
        </div>
        <div className="bg-brand-dark/60 border border-brand-line rounded-lg px-4 py-3">
          <p className="text-xs text-brand-creamMuted">Staked</p>
          <p className="text-lg font-bold text-brand-cream">{stakedBalance.toFixed(2)} TCL</p>
        </div>
      </div>

      {/* Mobile: lưới 2 cột thay vì 4 nút xếp chồng kín màn hình */}
      <div className="grid grid-cols-2 lg:flex gap-3">
        <input
          type="number"
          min="0.01"
          step="0.01"
          value={amount}
          onChange={(event) => setAmount(event.target.value)}
          placeholder="Amount TCL"
          className="col-span-2 w-full lg:w-44 bg-brand-dark/60 border border-brand-line rounded-lg px-4 py-2 text-sm text-brand-cream outline-none focus:border-brand-success/70"
        />

        <button
          type="button"
          onClick={handleDeposit}
          disabled={!canDeposit}
          className="px-4 py-2 bg-sky-500/10 text-sky-400 border border-sky-500/50 rounded-lg text-sm font-bold hover:bg-sky-500/20 disabled:opacity-50 disabled:cursor-not-allowed"
        >
          {isDepositing ? 'Depositing...' : 'Deposit TCL'}
        </button>

        <button
          type="button"
          onClick={handleWithdraw}
          disabled={!canWithdraw}
          className="px-4 py-2 bg-amber-500/10 text-amber-400 border border-amber-500/50 rounded-lg text-sm font-bold hover:bg-amber-500/20 disabled:opacity-50 disabled:cursor-not-allowed"
          title={hasValidAmount && requestedAmount > availableBalance ? 'Amount exceeds available balance' : undefined}
        >
          {isWithdrawing ? 'Withdrawing...' : 'Withdraw TCL'}
        </button>

        <button
          type="button"
          onClick={handleStake}
          disabled={!canStake}
          className="px-4 py-2 bg-emerald-500/10 text-emerald-400 border border-emerald-500/50 rounded-lg text-sm font-bold hover:bg-emerald-500/20 disabled:opacity-50 disabled:cursor-not-allowed"
        >
          {isStaking ? 'Staking...' : 'Stake TCL'}
        </button>

        <button
          type="button"
          onClick={handleUnstake}
          disabled={!canUnstake}
          className="px-4 py-2 bg-brand-panel text-brand-creamMuted border border-brand-line rounded-lg text-sm hover:bg-brand-dark disabled:opacity-50 disabled:cursor-not-allowed"
        >
          {isUnstaking ? 'Unstaking...' : 'Unstake TCL'}
        </button>
      </div>
    </div>
  );
}
