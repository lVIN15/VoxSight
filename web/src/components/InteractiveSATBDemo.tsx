import React, { useState } from 'react';
import type { VoicePart } from '../types';
import { Eye, Play, Pause, Volume2 } from 'lucide-react';

export const InteractiveSATBDemo: React.FC = () => {
  const [selectedPart, setSelectedPart] = useState<VoicePart>('Tenor');
  const [isPlaying, setIsPlaying] = useState<boolean>(false);
  const [activeNoteIndex, setActiveNoteIndex] = useState<number>(2);

  const voiceParts: VoicePart[] = ['Soprano', 'Alto', 'Tenor', 'Bass'];

  const scoreNotes = [
    { note: 'C4', pitchHz: 261.63 },
    { note: 'E4', pitchHz: 329.63 },
    { note: 'G4', pitchHz: 392.0 },
    { note: 'A4', pitchHz: 440.0 },
    { note: 'F4', pitchHz: 349.23 },
    { note: 'E4', pitchHz: 329.63 },
  ];

  const handlePlayToggle = () => {
    setIsPlaying(!isPlaying);
  };

  return (
    <div className="bg-white rounded-3xl p-5 sm:p-7 md:p-8 border border-violet-200 shadow-lg shadow-violet-100/50 my-8 text-left">
      <div className="flex flex-col lg:flex-row lg:items-center justify-between gap-5 mb-6 pb-6 border-b border-slate-200">
        <div>
          <div className="inline-flex items-center gap-2 text-xs font-mono font-bold text-violet-800 bg-violet-100 px-3 py-1 rounded-md mb-2">
            <Eye className="w-3.5 h-3.5" /> LIVE INTERACTIVE DEMO
          </div>
          <h3 className="text-xl sm:text-2xl font-bold text-slate-900">
            Audio-Visual Selective Focus Simulator
          </h3>
          <p className="text-xs sm:text-sm text-slate-600 mt-1">
            Select a voice part to experience how VoxSight dims unassigned staves to ≤20% opacity.
          </p>
        </div>

        {/* Voice Selector Buttons: Responsive 2x2 on small mobile, row on tablet/desktop */}
        <div className="grid grid-cols-2 sm:flex sm:flex-wrap items-center gap-2 bg-violet-50/80 p-1.5 rounded-2xl border border-violet-200 shrink-0">
          {voiceParts.map((part) => {
            const isSelected = selectedPart === part;
            return (
              <button
                key={part}
                type="button"
                onClick={() => setSelectedPart(part)}
                className={`px-3.5 py-2 text-xs font-bold rounded-xl transition-all min-h-[44px] flex items-center justify-center gap-1.5 ${
                  isSelected
                    ? 'bg-violet-800 text-white shadow-md shadow-violet-200 scale-105'
                    : 'text-slate-700 hover:text-violet-900 hover:bg-white'
                }`}
              >
                <span className="font-serif text-sm">{part[0]}</span>
                <span>{part}</span>
              </button>
            );
          })}
        </div>
      </div>

      {/* Simulated Score Display */}
      <div className="bg-slate-50 rounded-2xl p-4 sm:p-5 border border-slate-200 space-y-4 font-mono text-xs">
        
        {/* Controls Bar: Gracefully Wrapping */}
        <div className="flex flex-wrap items-center justify-between gap-3 text-slate-600 text-[11px] pb-3 border-b border-slate-200">
          <div className="flex items-center gap-2.5">
            <button
              onClick={handlePlayToggle}
              className="px-3.5 py-2 rounded-xl bg-violet-800 hover:bg-violet-900 text-white font-sans font-bold flex items-center gap-1.5 transition-colors shadow-sm min-h-[40px]"
            >
              {isPlaying ? <Pause className="w-3.5 h-3.5 text-amber-300" /> : <Play className="w-3.5 h-3.5 text-amber-300" />}
              <span>{isPlaying ? 'Pause Audio' : 'Play Vocal Audio'}</span>
            </button>
            <span className="hidden sm:inline text-slate-500 font-sans">"Sanctus in D Minor"</span>
          </div>

          <div className="flex flex-wrap items-center gap-2">
            <span className="text-violet-950 font-bold bg-violet-100 px-2.5 py-1 rounded border border-violet-200">
              Selected: {selectedPart}
            </span>
            <span className="px-2.5 py-1 rounded bg-emerald-100 text-emerald-900 text-[10px] font-bold border border-emerald-300 flex items-center gap-1">
              <Volume2 className="w-3 h-3 text-emerald-700" />
              <span>Vowel: "Aah"</span>
            </span>
          </div>
        </div>

        {/* 4 SATB Staves Grid with safe overflow handling */}
        <div className="space-y-3 py-1 overflow-x-auto">
          <div className="min-w-[300px] space-y-3">
            {voiceParts.map((part) => {
              const isAssigned = selectedPart === part;
              return (
                <div
                  key={part}
                  className={`transition-all duration-300 rounded-xl p-3 border ${
                    isAssigned
                      ? 'opacity-100 bg-white border-violet-400 shadow-md shadow-violet-100'
                      : 'opacity-25 bg-slate-100 border-slate-200'
                  }`}
                >
                  <div className="flex items-center gap-3">
                    {/* Clef & Part Label */}
                    <div className="w-14 flex items-center gap-1.5 shrink-0">
                      <span className="font-serif font-bold text-sm text-violet-800">{part[0]}</span>
                      <span className="text-[10px] font-sans text-slate-600 truncate font-semibold">{part}</span>
                    </div>

                    {/* Staff Lines and Noteheads */}
                    <div className="flex-1 relative h-9 flex items-center min-w-0">
                      {/* 5 Horizontal Staff Lines */}
                      <div className="absolute inset-0 flex flex-col justify-between opacity-30 pointer-events-none">
                        <div className="h-[1px] bg-slate-600 w-full" />
                        <div className="h-[1px] bg-slate-600 w-full" />
                        <div className="h-[1px] bg-slate-600 w-full" />
                        <div className="h-[1px] bg-slate-600 w-full" />
                        <div className="h-[1px] bg-slate-600 w-full" />
                      </div>

                      {/* Note heads */}
                      <div className="relative z-10 flex justify-between items-center w-full px-2 sm:px-4">
                        {scoreNotes.map((noteObj, idx) => {
                          const isActive = isAssigned && idx === activeNoteIndex;
                          return (
                            <button
                              key={idx}
                              type="button"
                              onClick={() => isAssigned && setActiveNoteIndex(idx)}
                              className={`relative flex flex-col items-center transition-transform min-w-[28px] min-h-[28px] justify-center ${
                                isActive ? 'scale-125 z-20' : ''
                              }`}
                              aria-label={`Note ${noteObj.note}`}
                            >
                              <div
                                className={`w-3.5 sm:w-4 h-3.5 sm:h-4 rounded-full border-2 transition-all ${
                                  isActive
                                    ? 'bg-amber-500 border-amber-300 shadow-md shadow-amber-500/80 animate-pulse'
                                    : isAssigned
                                    ? 'bg-violet-700 border-violet-900'
                                    : 'bg-slate-500 border-slate-400'
                                }`}
                              />
                              {isActive && (
                                <span className="absolute -top-4 text-[9px] font-bold text-amber-950 bg-amber-200 px-1 rounded border border-amber-300 pointer-events-none">
                                  {noteObj.note}
                                </span>
                              )}
                            </button>
                          );
                        })}
                      </div>
                    </div>
                  </div>
                </div>
              );
            })}
          </div>
        </div>

        {/* Status bar: Stacking on mobile, side-by-side on tablet/desktop */}
        <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between text-[11px] text-slate-600 pt-3 border-t border-slate-200 gap-2">
          <div className="flex items-center gap-2">
            <span className="w-2 h-2 rounded-full bg-emerald-600 animate-pulse shrink-0" />
            <span>Opacity Reduction: Unassigned staves dimmed to <strong>≤20%</strong></span>
          </div>
          <span className="text-violet-800 font-sans font-semibold">
            Suppressed non-assigned background audio volume
          </span>
        </div>

      </div>
    </div>
  );
};
