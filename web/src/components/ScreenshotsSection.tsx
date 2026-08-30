import React, { useState } from 'react';
import { APP_SCREENSHOTS } from '../data/mockData';
import { Smartphone, Scan, Check } from 'lucide-react';
import { InteractivePitchDemo } from './InteractivePitchDemo';

export const ScreenshotsSection: React.FC = () => {
  const [activeTab, setActiveTab] = useState<string>('focus');

  const activeScreenshot = APP_SCREENSHOTS.find((s) => s.id === activeTab) || APP_SCREENSHOTS[1];

  return (
    <section id="showcase" className="py-20 md:py-28 relative bg-[#F7F4EE] border-b border-[#EBE3D7]">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        
        {/* Section Header */}
        <div className="max-w-3xl space-y-4 mb-16 text-left">
          <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-violet-100 border border-violet-200">
            <Smartphone className="w-3.5 h-3.5 text-violet-800" />
            <span className="text-xs font-sans font-bold text-violet-950 tracking-wider">
              APPLICATION SHOWCASE
            </span>
          </div>

          <h2 className="text-3xl sm:text-4xl lg:text-5xl font-serif font-extrabold text-slate-900 tracking-tight leading-tight">
            Inside VoxSight <br />
            <span className="text-gradient-violet italic font-normal">Android Application</span>
          </h2>

          <p className="text-base font-sans text-slate-600 leading-relaxed">
            Explore the clean, mobile-first interface engineered specifically for choral sight-reading practice.
          </p>
        </div>

        {/* Tab Selection */}
        <div className="flex flex-wrap items-center justify-start gap-3 mb-12">
          {APP_SCREENSHOTS.map((screen) => {
            const isActive = activeTab === screen.id;
            return (
              <button
                key={screen.id}
                onClick={() => setActiveTab(screen.id)}
                className={`px-5 py-2.5 rounded-2xl text-xs font-sans font-bold transition-all border ${
                  isActive
                    ? 'bg-violet-900 text-white border-violet-950 shadow-md shadow-violet-900/15 scale-105'
                    : 'bg-white text-slate-700 border-[#E5D7C5] hover:text-violet-900 hover:bg-violet-50'
                }`}
              >
                {screen.title}
              </button>
            );
          })}
        </div>

        {/* Grounded Phone Showcase Card */}
        <div className="bg-white rounded-3xl p-8 md:p-12 border border-[#EBE2D5] shadow-xl shadow-violet-900/5 mb-12">
          <div className="grid grid-cols-1 lg:grid-cols-12 gap-8 items-center">
            
            {/* Left Description */}
            <div className="lg:col-span-5 space-y-5 text-left">
              <span className="px-3 py-1 text-xs font-mono font-bold bg-amber-100/80 text-amber-900 rounded-full border border-amber-300">
                {activeScreenshot.category}
              </span>
              <h3 className="text-2xl sm:text-3xl font-serif font-bold text-slate-900">
                {activeScreenshot.title}
              </h3>
              <p className="text-sm sm:text-base font-sans text-slate-600 leading-relaxed">
                {activeScreenshot.caption}
              </p>

              <div className="pt-4 border-t border-[#F0E6D8] space-y-3">
                <div className="flex items-center gap-3 text-xs font-sans text-slate-700">
                  <Check className="w-4 h-4 text-emerald-700 shrink-0" />
                  <span>Native Android Jetpack Compose UI</span>
                </div>
                <div className="flex items-center gap-3 text-xs font-sans text-slate-700">
                  <Check className="w-4 h-4 text-emerald-700 shrink-0" />
                  <span>High-contrast clear notation display</span>
                </div>
                <div className="flex items-center gap-3 text-xs font-sans text-slate-700">
                  <Check className="w-4 h-4 text-emerald-700 shrink-0" />
                  <span>Sub-0.1s synchronized playhead updates</span>
                </div>
              </div>
            </div>

            {/* Right Phone Frame */}
            <div className="lg:col-span-7 flex justify-center">
              <div className="relative w-full max-w-md bg-slate-900 border-[6px] border-slate-800 rounded-[36px] p-4 shadow-2xl shadow-violet-950/20">
                
                {/* Status Bar */}
                <div className="flex justify-between items-center text-[10px] font-mono text-slate-400 px-2 pb-2 border-b border-slate-800 mb-3">
                  <span>9:41 AM</span>
                  <span className="text-violet-400 font-bold">VoxSight Android</span>
                  <span>100% ⚡</span>
                </div>

                {/* Tab Specific Screen Content */}
                {activeTab === 'scan' && (
                  <div className="bg-[#FAF8F5] rounded-2xl p-4 text-left space-y-4 font-mono text-xs text-slate-900 border border-slate-200">
                    <div className="flex justify-between items-center pb-2 border-b border-slate-200">
                      <span className="text-violet-900 font-bold font-sans">Score Scanner</span>
                      <span className="text-[10px] bg-emerald-100 text-emerald-900 px-2 py-0.5 rounded font-bold">OMR Active</span>
                    </div>
                    <div className="h-48 border-2 border-dashed border-violet-300 rounded-xl bg-violet-50/60 flex flex-col items-center justify-center gap-2 p-4 text-center">
                      <Scan className="w-8 h-8 text-violet-800 animate-pulse" />
                      <span className="text-slate-800 font-sans font-bold text-xs">Scanning Sheet Music...</span>
                      <span className="text-[10px] text-slate-500 font-sans">Target SATB separation accuracy: 80–85%</span>
                    </div>
                    <div className="bg-white p-3 rounded-xl border border-slate-200 text-[11px]">
                      <div className="text-emerald-800 font-bold mb-1 font-sans">Parsed Output: MusicXML</div>
                      <div className="text-slate-600">4 Staves Detected (S, A, T, B) • Key: D Minor</div>
                    </div>
                  </div>
                )}

                {activeTab === 'focus' && (
                  <div className="bg-[#FAF8F5] rounded-2xl p-4 text-left space-y-3 font-mono text-xs text-slate-900 border border-slate-200">
                    <div className="flex justify-between items-center pb-2 border-b border-slate-200">
                      <span className="text-violet-900 font-bold font-sans">Selective Focus</span>
                      <span className="text-amber-950 text-[10px] font-bold bg-amber-100 px-2 py-0.5 rounded border border-amber-300">TENOR ISOLATED</span>
                    </div>
                    <div className="space-y-2 py-2">
                      <div className="opacity-25 flex items-center justify-between p-2 rounded bg-slate-100 text-slate-500">
                        <span>Soprano Line</span> <span>[Dimmed ≤20%]</span>
                      </div>
                      <div className="opacity-25 flex items-center justify-between p-2 rounded bg-slate-100 text-slate-500">
                        <span>Alto Line</span> <span>[Dimmed ≤20%]</span>
                      </div>
                      <div className="opacity-100 p-3 rounded-xl bg-violet-100 border border-violet-300 flex items-center justify-between shadow-sm">
                        <span className="text-violet-950 font-bold">★ Tenor Line (Isolated)</span>
                        <span className="text-emerald-800 font-bold">100% Opacity</span>
                      </div>
                      <div className="opacity-25 flex items-center justify-between p-2 rounded bg-slate-100 text-slate-500">
                        <span>Bass Line</span> <span>[Dimmed ≤20%]</span>
                      </div>
                    </div>
                    <div className="text-[10px] text-slate-600 bg-white p-2 rounded border border-slate-200">
                      Audio: Sustained human vocal soundfont ("Aah")
                    </div>
                  </div>
                )}

                {activeTab === 'pitch' && (
                  <div className="bg-[#FAF8F5] rounded-2xl p-4 text-left space-y-3 font-mono text-xs text-slate-900 border border-slate-200">
                    <div className="flex justify-between items-center pb-2 border-b border-slate-200">
                      <span className="text-emerald-800 font-bold font-sans">Real-Time Pitch Feedback</span>
                      <span className="text-[10px] bg-emerald-100 text-emerald-900 px-2 py-0.5 rounded font-bold">MIC ACTIVE</span>
                    </div>
                    <div className="p-4 rounded-xl bg-white border border-emerald-300 text-center space-y-2">
                      <div className="text-xs text-slate-500 font-sans">Sung Frequency Detected</div>
                      <div className="text-2xl font-black text-emerald-800">441.2 Hz</div>
                      <div className="text-[11px] text-emerald-900 font-bold bg-emerald-100 py-1 rounded border border-emerald-200">
                        IN TUNE (+2 cents vs Target A4)
                      </div>
                    </div>
                    <div className="text-[10px] text-slate-600 bg-white p-2.5 rounded border border-slate-200 flex justify-between">
                      <span>Mic Delay: ≤0.5s</span>
                      <span className="text-violet-900 font-bold">YIN Pitch Algorithm</span>
                    </div>
                  </div>
                )}

                {activeTab === 'analytics' && (
                  <div className="bg-[#FAF8F5] rounded-2xl p-4 text-left space-y-3 font-mono text-xs text-slate-900 border border-slate-200">
                    <div className="flex justify-between items-center pb-2 border-b border-slate-200">
                      <span className="text-violet-900 font-bold font-sans">Session Summary</span>
                      <span className="text-[10px] bg-violet-100 text-violet-950 px-2 py-0.5 rounded font-bold">Score: 92%</span>
                    </div>
                    <div className="grid grid-cols-2 gap-2 text-[11px]">
                      <div className="p-2.5 rounded-lg bg-white border border-slate-200">
                        <div className="text-slate-500 text-[10px] font-sans">Pitch Match Rate</div>
                        <div className="text-base font-bold text-emerald-800">92%</div>
                      </div>
                      <div className="p-2.5 rounded-lg bg-white border border-slate-200">
                        <div className="text-slate-500 text-[10px] font-sans">Avg Sync Latency</div>
                        <div className="text-base font-bold text-violet-900">0.08s</div>
                      </div>
                    </div>
                    <div className="bg-white p-3 rounded-xl border border-slate-200 space-y-1.5 text-[10px]">
                      <div className="flex justify-between text-slate-700">
                        <span>Measure 1–4</span> <span className="text-emerald-800 font-bold">100% In Tune</span>
                      </div>
                      <div className="flex justify-between text-slate-700">
                        <span>Measure 5–8 (High A4)</span> <span className="text-amber-800 font-bold">84% Match</span>
                      </div>
                    </div>
                  </div>
                )}

              </div>
            </div>

          </div>
        </div>

        {/* Live Pitch Feedback Simulator */}
        <InteractivePitchDemo />

      </div>
    </section>
  );
};
