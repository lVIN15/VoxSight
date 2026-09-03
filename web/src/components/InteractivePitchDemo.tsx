import React, { useState } from 'react';
import { Activity, CheckCircle, AlertTriangle } from 'lucide-react';
import { TuningForkIcon } from './icons/CustomMusicIcons';

export const InteractivePitchDemo: React.FC = () => {
  const [centsOffset, setCentsOffset] = useState<number>(0);
  const targetNote = 'A4 (440.0 Hz)';

  const getStatus = (cents: number) => {
    if (Math.abs(cents) <= 10) {
      return {
        label: 'IN TUNE',
        color: 'text-emerald-800',
        bgColor: 'bg-emerald-100 border-emerald-300',
        barColor: 'bg-emerald-600',
        message: 'Excellent intonation! Sung vocal frequency matches target pitch.'
      };
    } else if (cents < -10) {
      return {
        label: `FLAT (${cents}c)`,
        color: 'text-amber-900',
        bgColor: 'bg-amber-100 border-amber-300',
        barColor: 'bg-amber-600',
        message: 'Singing slightly flat. Lift vocal palette to match reference pitch.'
      };
    } else {
      return {
        label: `SHARP (+${cents}c)`,
        color: 'text-purple-900',
        bgColor: 'bg-purple-100 border-purple-300',
        barColor: 'bg-purple-600',
        message: 'Singing slightly sharp. Ease vocal tension to match reference pitch.'
      };
    }
  };

  const currentStatus = getStatus(centsOffset);
  const pointerPercentage = Math.max(0, Math.min(100, ((centsOffset + 50) / 100) * 100));

  return (
    <div className="bg-white rounded-3xl p-5 sm:p-7 md:p-8 border border-[#EBE2D5] shadow-lg shadow-violet-900/5 my-8 text-left">
      <div className="flex flex-col lg:flex-row lg:items-center justify-between gap-5 mb-6 pb-5 border-b border-[#F0E6D8]">
        <div>
          <div className="inline-flex items-center gap-2 text-xs font-mono font-bold text-emerald-900 bg-emerald-100 px-3 py-1 rounded-md mb-2">
            <TuningForkIcon className="w-4 h-4 text-emerald-800" /> LIVE PITCH SIMULATOR
          </div>
          <h3 className="text-xl sm:text-2xl font-serif font-bold text-slate-900">
            Real-Time Pitch Feedback Meter
          </h3>
          <p className="text-xs sm:text-sm font-sans text-slate-600 mt-1">
            Adjust the intonation slider to test how VoxSight detects pitch deviations within ≤0.5s latency.
          </p>
        </div>

        {/* Quick Presets: Responsive wrapping with equal-width buttons on mobile */}
        <div className="flex flex-wrap items-center gap-2 shrink-0">
          <button
            type="button"
            onClick={() => setCentsOffset(-25)}
            className="flex-1 sm:flex-initial px-3 py-2 text-xs font-mono font-bold rounded-xl bg-amber-50 hover:bg-amber-100 text-amber-950 border border-amber-300 transition-colors min-h-[42px]"
          >
            Flat (-25c)
          </button>
          <button
            type="button"
            onClick={() => setCentsOffset(0)}
            className="flex-1 sm:flex-initial px-3 py-2 text-xs font-mono font-bold rounded-xl bg-emerald-50 hover:bg-emerald-100 text-emerald-950 border border-emerald-300 transition-colors min-h-[42px]"
          >
            In-Tune (0c)
          </button>
          <button
            type="button"
            onClick={() => setCentsOffset(30)}
            className="flex-1 sm:flex-initial px-3 py-2 text-xs font-mono font-bold rounded-xl bg-purple-50 hover:bg-purple-100 text-purple-950 border border-purple-300 transition-colors min-h-[42px]"
          >
            Sharp (+30c)
          </button>
        </div>
      </div>

      {/* Meter Container */}
      <div className="bg-[#FAF8F5] rounded-2xl p-4 sm:p-6 border border-slate-200 space-y-5">
        
        {/* Info Bar */}
        <div className="flex flex-wrap items-center justify-between gap-3 text-xs font-mono">
          <div className="flex items-center gap-2 sm:gap-3">
            <span className="text-slate-600 font-sans">Target Pitch:</span>
            <span className="text-violet-950 font-bold text-xs sm:text-sm bg-violet-100 px-2.5 py-1 rounded border border-violet-200">
              {targetNote}
            </span>
          </div>

          <div className={`inline-flex items-center gap-2 px-3 py-1.5 rounded-lg border font-bold text-xs ${currentStatus.bgColor} ${currentStatus.color}`}>
            <Activity className="w-4 h-4 shrink-0" />
            <span>{currentStatus.label}</span>
          </div>
        </div>

        {/* Visual Intonation Meter */}
        <div className="space-y-2">
          {/* Responsive labels: Abbreviated on small mobile to prevent any collision */}
          <div className="flex justify-between text-[10px] sm:text-[11px] font-mono text-slate-600">
            <span className="text-amber-800 font-bold">
              <span className="hidden sm:inline">-50 Cents (Flat)</span>
              <span className="sm:hidden">-50c Flat</span>
            </span>
            <span className="text-emerald-800 font-bold text-center px-1">
              <span className="hidden sm:inline">0 Cents (Target)</span>
              <span className="sm:hidden">0c Target</span>
            </span>
            <span className="text-purple-800 font-bold text-right">
              <span className="hidden sm:inline">+50 Cents (Sharp)</span>
              <span className="sm:hidden">+50c Sharp</span>
            </span>
          </div>

          {/* Meter Track */}
          <div className="relative h-6 bg-slate-200 rounded-full border border-slate-300 overflow-hidden shadow-inner">
            <div className="absolute left-[40%] right-[40%] top-0 bottom-0 bg-emerald-200/80 border-x border-emerald-400" />
            <div
              className={`absolute top-0 bottom-0 w-2.5 rounded-full shadow-md transition-all duration-200 ${currentStatus.barColor}`}
              style={{ left: `calc(${pointerPercentage}% - 5px)` }}
            />
          </div>

          {/* Slider Input */}
          <div className="pt-3">
            <div className="text-xs font-mono text-slate-600 mb-2 flex flex-wrap justify-between gap-1">
              <label htmlFor="pitch-cents-input">Simulate Vocal Frequency Offset:</label>
              <span className="text-slate-900 font-bold">{centsOffset > 0 ? `+${centsOffset}` : centsOffset} cents</span>
            </div>
            <input
              id="pitch-cents-input"
              type="range"
              min="-50"
              max="50"
              value={centsOffset}
              onChange={(e) => setCentsOffset(parseInt(e.target.value))}
              className="w-full h-2.5 bg-slate-200 rounded-lg appearance-none cursor-pointer accent-violet-900"
              aria-label="Adjust vocal pitch deviation in cents"
            />
          </div>
        </div>

        {/* Guidance Box */}
        <div className="p-3.5 rounded-xl bg-white border border-slate-200 text-xs font-sans text-slate-700 flex items-center gap-3 shadow-xs">
          {Math.abs(centsOffset) <= 10 ? (
            <CheckCircle className="w-5 h-5 text-emerald-700 shrink-0" />
          ) : (
            <AlertTriangle className="w-5 h-5 text-amber-700 shrink-0" />
          )}
          <span className="leading-relaxed">{currentStatus.message}</span>
        </div>

      </div>
    </div>
  );
};
