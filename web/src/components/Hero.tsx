import React from 'react';
import { Download, ArrowRight, Music, Mic } from 'lucide-react';
import { PROJECT_INFO } from '../data/mockData';

export const Hero: React.FC = () => {
  return (
    <section className="relative pt-32 pb-20 md:pt-40 md:pb-28 overflow-hidden bg-stave-texture bg-[#FAF8F5]">
      {/* Soft Ambient Gold & Violet Background Glows */}
      <div className="absolute top-1/4 left-1/2 -translate-x-1/2 -translate-y-1/2 w-[600px] h-[400px] bg-gradient-to-tr from-amber-200/30 via-violet-200/30 to-purple-100/40 blur-[120px] rounded-full pointer-events-none" />

      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 relative z-10">
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-12 items-center">
          
          {/* Left Column: Asymmetric Content */}
          <div className="lg:col-span-7 space-y-6 text-center lg:text-left">
            
            {/* Capstone & Academic Badge */}
            <div className="inline-flex items-center gap-2 px-3.5 py-1.5 rounded-full bg-violet-100/80 border border-violet-200 backdrop-blur-sm">
              <span className="flex h-2 w-2 rounded-full bg-amber-600 animate-pulse" />
              <span className="text-xs font-sans font-bold text-violet-950 tracking-wide">
                CAPSTONE INITIATIVE • TEAM {PROJECT_INFO.teamCode}
              </span>
            </div>

            {/* Serif Main Headline */}
            <h1 className="text-4xl sm:text-5xl lg:text-6xl font-serif font-extrabold tracking-tight text-slate-900 leading-[1.15]">
              Master Independent <br />
              <span className="text-gradient-violet italic font-normal">Choral Sight-Reading</span> <br />
              with <span className="text-violet-900">VoxSight</span>
            </h1>

            {/* Subtitle */}
            <p className="text-base sm:text-lg text-slate-600 font-sans leading-relaxed max-w-2xl mx-auto lg:mx-0">
              An Android mobile companion created for amateur choir members. Scans printed choir sheet music into digital scores, isolates your vocal part with staff dimming, plays sustained vocal soundfonts, and gives real-time microphone pitch feedback.
            </p>

            {/* Action Buttons */}
            <div className="flex flex-col sm:flex-row items-center justify-center lg:justify-start gap-4 pt-2">
              <a
                href="#download"
                className="w-full sm:w-auto inline-flex items-center justify-center gap-3 px-7 py-3.5 text-sm font-sans font-bold text-white bg-gradient-to-r from-violet-900 via-violet-800 to-indigo-800 hover:from-violet-950 hover:to-indigo-900 rounded-2xl shadow-lg shadow-violet-900/15 hover:-translate-y-0.5 transition-all duration-200"
              >
                <Download className="w-5 h-5 text-amber-300" />
                <span>Download Android APK</span>
                <span className="text-[11px] font-mono opacity-80">({PROJECT_INFO.apkSize})</span>
              </a>

              <a
                href="#features"
                className="w-full sm:w-auto inline-flex items-center justify-center gap-2 px-6 py-3.5 text-sm font-sans font-semibold text-slate-700 hover:text-violet-900 bg-[#F4EFEA] hover:bg-[#EBE3DB] border border-[#E3D7CB] rounded-2xl transition-all duration-200"
              >
                <span>Explore Core Features</span>
                <ArrowRight className="w-4 h-4 text-amber-700" />
              </a>
            </div>

            {/* System Metrics Grid */}
            <div className="pt-6 grid grid-cols-2 sm:grid-cols-4 gap-3 border-t border-[#EFE5DB]">
              <div className="p-3 rounded-xl bg-white border border-[#E9DFD5] shadow-sm">
                <div className="text-[11px] text-slate-500 font-sans font-medium">OMR Scan Speed</div>
                <div className="text-sm sm:text-base font-mono font-bold text-slate-900">≤10s / page</div>
              </div>
              <div className="p-3 rounded-xl bg-white border border-[#E9DFD5] shadow-sm">
                <div className="text-[11px] text-slate-500 font-sans font-medium">Staff Dimming</div>
                <div className="text-sm sm:text-base font-mono font-bold text-violet-900">≤20% Opacity</div>
              </div>
              <div className="p-3 rounded-xl bg-white border border-[#E9DFD5] shadow-sm">
                <div className="text-[11px] text-slate-500 font-sans font-medium">Playhead Sync</div>
                <div className="text-sm sm:text-base font-mono font-bold text-emerald-800">≤0.1s Latency</div>
              </div>
              <div className="p-3 rounded-xl bg-white border border-[#E9DFD5] shadow-sm">
                <div className="text-[11px] text-slate-500 font-sans font-medium">Pitch Match</div>
                <div className="text-sm sm:text-base font-mono font-bold text-amber-800">≥85% Accuracy</div>
              </div>
            </div>

          </div>

          {/* Right Column: Grounded Phone Mockup over Printed Sheet Music Backdrop */}
          <div className="lg:col-span-5 relative flex justify-center">
            
            {/* Layer 1: Stylized Printed Sheet Music Backdrop Card */}
            <div className="absolute inset-0 bg-[#FAF3E8] border border-[#E5D7C5] rounded-3xl transform rotate-3 shadow-xl flex flex-col justify-between p-6 overflow-hidden">
              <div className="opacity-15 font-serif text-[10px] text-slate-800 space-y-1 leading-none select-none pointer-events-none">
                <div>SANCTUS IN D MINOR — CHORAL SCORE</div>
                <div className="h-0.5 bg-slate-800 w-full" />
                <div className="h-0.5 bg-slate-800 w-full" />
                <div className="h-0.5 bg-slate-800 w-full" />
                <div className="h-0.5 bg-slate-800 w-full" />
                <div className="h-0.5 bg-slate-800 w-full" />
              </div>
              <div className="opacity-15 font-serif text-[10px] text-slate-800 space-y-1 leading-none select-none pointer-events-none">
                <div className="h-0.5 bg-slate-800 w-full" />
                <div className="h-0.5 bg-slate-800 w-full" />
                <div className="h-0.5 bg-slate-800 w-full" />
                <div className="h-0.5 bg-slate-800 w-full" />
                <div className="h-0.5 bg-slate-800 w-full" />
              </div>
            </div>

            {/* Warm Gold Backdrop Ring */}
            <div className="absolute inset-0 bg-gradient-to-tr from-amber-400/25 via-violet-400/20 to-indigo-500/20 rounded-3xl blur-xl transform -rotate-2" />

            {/* Layer 2: Android Device Frame */}
            <div className="relative z-10 w-full max-w-[340px] bg-slate-900 border-[6px] border-slate-800 rounded-[42px] p-3 shadow-2xl shadow-violet-950/20">
              
              {/* Phone Speaker Notch */}
              <div className="w-24 h-4 bg-slate-800 rounded-full mx-auto mb-3 flex items-center justify-center gap-2">
                <div className="w-2 h-2 rounded-full bg-slate-600" />
                <div className="w-8 h-1 rounded-full bg-slate-700" />
              </div>

              {/* App Screen Display */}
              <div className="bg-white rounded-[32px] p-4 text-left overflow-hidden border border-slate-200 space-y-3.5 shadow-inner">
                
                {/* App Bar */}
                <div className="flex items-center justify-between border-b border-slate-100 pb-2.5">
                  <div className="flex items-center gap-2">
                    <div className="w-7 h-7 rounded-lg bg-violet-900 flex items-center justify-center text-amber-300">
                      <Music className="w-4 h-4" />
                    </div>
                    <div>
                      <div className="text-xs font-bold text-slate-900 font-sans">VoxSight Practice</div>
                      <div className="text-[10px] text-violet-900 font-mono font-bold">Tenor Line • Isolated</div>
                    </div>
                  </div>
                  <span className="px-2 py-0.5 text-[9px] font-mono font-bold bg-emerald-100 text-emerald-900 rounded-full border border-emerald-300">
                    MIC ACTIVE
                  </span>
                </div>

                {/* Score Staff Preview */}
                <div className="bg-[#FAF8F5] rounded-xl p-3 border border-slate-200 space-y-2">
                  <div className="flex justify-between items-center text-[10px] text-slate-500 font-sans">
                    <span className="font-medium">Excerpt: "Sanctus in D Minor"</span>
                    <span className="text-violet-900 font-mono font-bold">BPM: 92</span>
                  </div>

                  {/* 4 SATB Staves Visualizer */}
                  <div className="space-y-1.5 py-1 font-mono text-[10px]">
                    {/* Soprano Staff (Dimmed) */}
                    <div className="opacity-20 flex items-center gap-2">
                      <span className="w-4 text-slate-600 font-bold">S</span>
                      <div className="flex-1 h-0.5 bg-slate-400 relative">
                        <div className="absolute top-1/2 left-1/4 -translate-y-1/2 w-2 h-2 rounded-full bg-slate-500" />
                      </div>
                    </div>

                    {/* Alto Staff (Dimmed) */}
                    <div className="opacity-20 flex items-center gap-2">
                      <span className="w-4 text-slate-600 font-bold">A</span>
                      <div className="flex-1 h-0.5 bg-slate-400 relative">
                        <div className="absolute top-1/2 left-1/3 -translate-y-1/2 w-2 h-2 rounded-full bg-slate-500" />
                      </div>
                    </div>

                    {/* Tenor Staff (ACTIVE 100% Opacity) */}
                    <div className="opacity-100 bg-violet-100/90 p-1.5 rounded-lg border border-violet-300 flex items-center gap-2 shadow-sm">
                      <span className="w-4 text-violet-950 font-bold">T</span>
                      <div className="flex-1 h-0.5 bg-violet-700 relative">
                        {/* Active Playhead Notehead */}
                        <div className="absolute top-1/2 left-1/2 -translate-y-1/2 w-3 h-3 rounded-full bg-amber-500 shadow-md shadow-amber-500/80 animate-ping" />
                        <div className="absolute top-1/2 left-1/2 -translate-y-1/2 w-3 h-3 rounded-full bg-amber-500 shadow-md shadow-amber-500/80" />
                      </div>
                      <span className="text-[9px] text-amber-950 font-bold px-1.5 py-0.5 rounded bg-amber-200 border border-amber-300">
                        A3 (220 Hz)
                      </span>
                    </div>

                    {/* Bass Staff (Dimmed) */}
                    <div className="opacity-20 flex items-center gap-2">
                      <span className="w-4 text-slate-600 font-bold">B</span>
                      <div className="flex-1 h-0.5 bg-slate-400 relative">
                        <div className="absolute top-1/2 left-1/2 -translate-y-1/2 w-2 h-2 rounded-full bg-slate-500" />
                      </div>
                    </div>
                  </div>
                </div>

                {/* Real-Time Pitch Meter */}
                <div className="bg-[#FAF8F5] rounded-xl p-3 border border-slate-200 space-y-2">
                  <div className="flex items-center justify-between text-xs font-sans">
                    <span className="text-slate-800 font-semibold flex items-center gap-1.5">
                      <Mic className="w-3.5 h-3.5 text-emerald-700" /> Vocal Input
                    </span>
                    <span className="text-emerald-800 font-bold font-mono text-[11px]">
                      IN TUNE (+2c)
                    </span>
                  </div>

                  {/* Meter Bar */}
                  <div className="relative w-full h-3 bg-slate-200 rounded-full overflow-hidden border border-slate-300">
                    <div className="absolute left-1/2 -translate-x-1/2 w-8 h-full bg-emerald-200" />
                    <div className="absolute left-[52%] top-0 bottom-0 w-1.5 bg-emerald-600 rounded-full shadow" />
                  </div>

                  <div className="flex justify-between text-[9px] text-slate-500 font-mono">
                    <span>FLAT (-50c)</span>
                    <span className="text-emerald-800 font-bold">TARGET (A3)</span>
                    <span>SHARP (+50c)</span>
                  </div>
                </div>

                {/* Vocal Soundfont Indicator */}
                <div className="p-2 rounded-xl bg-violet-50 border border-violet-200 flex items-center justify-between font-sans">
                  <div className="flex items-center gap-2">
                    <div className="flex items-end gap-1 h-3.5">
                      <span className="w-1 bg-violet-700 rounded-full animate-wave-bar" style={{ animationDelay: '0s' }} />
                      <span className="w-1 bg-emerald-600 rounded-full animate-wave-bar" style={{ animationDelay: '0.2s' }} />
                      <span className="w-1 bg-violet-700 rounded-full animate-wave-bar" style={{ animationDelay: '0.4s' }} />
                    </div>
                    <span className="text-[10px] text-violet-950 font-bold">
                      Human "Aah" Vocal Soundfont
                    </span>
                  </div>
                  <span className="text-[10px] font-mono text-slate-500">≤0.1s Sync</span>
                </div>

              </div>

            </div>
          </div>

        </div>
      </div>
    </section>
  );
};
