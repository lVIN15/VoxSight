import React, { useState } from 'react';
import type { VoicePart } from '../types';
import { Eye, Play, Pause } from 'lucide-react';

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
    <div className="bg-white rounded-3xl p-6 sm:p-8 border border-violet-200 shadow-lg shadow-violet-100/50 my-8">
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 mb-6 pb-6 border-b border-slate-200">
        <div>
          <div className="inline-flex items-center gap-2 text-xs font-mono font-bold text-violet-800 bg-violet-100 px-3 py-1 rounded-md mb-2">
            <Eye className="w-3.5 h-3.5" /> LIVE INTERACTIVE DEMO
          </div>
          <h3 className="text-xl sm:text-2xl font-bold text-slate-900">
            Audio-Visual Selective Focus Simulator
          </h3>
          <p className="text-xs sm:text-sm text-slate-600">
            Select a voice part to experience how VoxSight dims unassigned staves to ≤20% opacity.
          </p>
        </div>

        {/* Voice Selector Buttons */}
        <div className="flex flex-wrap items-center gap-2 bg-violet-50 p-1.5 rounded-2xl border border-violet-200">
          {voiceParts.map((part) => {
            const isSelected = selectedPart === part;
            return (
              <button
                key={part}
                onClick={() => setSelectedPart(part)}
                className={`px-4 py-2 text-xs font-bold rounded-xl transition-all ${
                  isSelected
                    ? 'bg-violet-700 text-white shadow-md shadow-violet-200 scale-105'
                    : 'text-slate-700 hover:text-violet-900 hover:bg-white'
                }`}
              >
                {part}
              </button>
            );
          })}
        </div>
      </div>

      {/* Simulated Score Display */}
      <div className="bg-slate-50 rounded-2xl p-5 border border-slate-200 space-y-4 font-mono text-xs">
        
        {/* Controls Bar */}
        <div className="flex items-center justify-between text-slate-600 text-[11px] pb-2 border-b border-slate-200">
          <div className="flex items-center gap-3">
            <button
              onClick={handlePlayToggle}
              className="px-3.5 py-1.5 rounded-lg bg-violet-700 hover:bg-violet-800 text-white font-sans font-bold flex items-center gap-1.5 transition-colors shadow-sm"
            >
              {isPlaying ? <Pause className="w-3.5 h-3.5" /> : <Play className="w-3.5 h-3.5" />}
              {isPlaying ? 'Pause Audio' : 'Play Vocal Audio'}
            </button>
            <span className="hidden sm:inline text-slate-500 font-sans">Excerpt: "Sanctus in D Minor"</span>
          </div>

          <div className="flex items-center gap-2">
            <span className="text-violet-900 font-bold">Selected: {selectedPart}</span>
            <span className="px-2 py-0.5 rounded bg-emerald-100 text-emerald-800 text-[10px] font-bold border border-emerald-200">
              Vowel: "Aah" Soundfont
            </span>
          </div>
        </div>

        {/* 4 SATB Staves Grid */}
        <div className="space-y-3 py-2">
          {voiceParts.map((part) => {
            const isAssigned = selectedPart === part;
            return (
              <div
                key={part}
                className={`transition-all duration-300 rounded-xl p-3 border ${
                  isAssigned
                    ? 'opacity-100 bg-white border-violet-400 shadow-md shadow-violet-100 scale-[1.01]'
                    : 'opacity-25 bg-slate-100 border-slate-200'
                }`}
              >
                <div className="flex items-center gap-4">
                  {/* Clef & Part Label */}
                  <div className="w-16 flex items-center gap-2 shrink-0">
                    <span className="font-bold text-sm text-violet-800">{part[0]}</span>
                    <span className="text-[10px] font-sans text-slate-600 truncate font-semibold">{part}</span>
                  </div>

                  {/* Staff Lines and Noteheads */}
                  <div className="flex-1 relative h-10 flex items-center">
                    {/* 5 Horizontal Staff Lines */}
                    <div className="absolute inset-0 flex flex-col justify-between opacity-30 pointer-events-none">
                      <div className="h-[1px] bg-slate-600 w-full" />
                      <div className="h-[1px] bg-slate-600 w-full" />
                      <div className="h-[1px] bg-slate-600 w-full" />
                      <div className="h-[1px] bg-slate-600 w-full" />
                      <div className="h-[1px] bg-slate-600 w-full" />
                    </div>

                    {/* Note heads */}
                    <div className="relative z-10 flex justify-between items-center w-full px-4">
                      {scoreNotes.map((noteObj, idx) => {
                        const isActive = isAssigned && idx === activeNoteIndex;
                        return (
                          <button
                            key={idx}
                            onClick={() => isAssigned && setActiveNoteIndex(idx)}
                            className={`relative flex flex-col items-center group transition-transform ${
                              isActive ? 'scale-125' : ''
                            }`}
                          >
                            <div
                              className={`w-4 h-4 rounded-full border-2 transition-all ${
                                isActive
                                  ? 'bg-amber-500 border-amber-300 shadow-md shadow-amber-500/80 animate-pulse'
                                  : isAssigned
                                  ? 'bg-violet-700 border-violet-900'
                                  : 'bg-slate-500 border-slate-400'
                              }`}
                            />
                            {isActive && (
                              <span className="absolute -bottom-5 text-[9px] font-bold text-amber-900 bg-amber-100 px-1 rounded border border-amber-300">
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

        {/* Status bar */}
        <div className="flex flex-col sm:flex-row items-center justify-between text-[11px] text-slate-600 pt-2 border-t border-slate-200">
          <div className="flex items-center gap-2">
            <span className="w-2 h-2 rounded-full bg-emerald-600 animate-pulse" />
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
