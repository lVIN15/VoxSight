import React from 'react';
import { Download, ArrowRight, Music, Mic } from 'lucide-react';
import { PROJECT_INFO } from '../data/mockData';

export const Hero: React.FC = () => {
  return (
    <section className="relative pt-28 pb-16 sm:pt-36 sm:pb-24 md:pt-40 md:pb-28 overflow-hidden bg-stave-texture bg-[#FAF8F5]">
      {/* Soft Ambient Gold & Violet Background Glows */}
      <div className="absolute top-1/4 left-1/2 -translate-x-1/2 -translate-y-1/2 w-[320px] sm:w-[500px] lg:w-[600px] h-[300px] sm:h-[400px] bg-gradient-to-tr from-amber-200/30 via-violet-200/30 to-purple-100/40 blur-[90px] sm:blur-[120px] rounded-full pointer-events-none" />

      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 relative z-10">
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-10 lg:gap-12 items-center">
          
          {/* Left Column: Asymmetric Content */}
          <div className="lg:col-span-7 space-y-5 sm:space-y-6 text-center lg:text-left">
            
            {/* Capstone & Academic Badge */}
            <div className="inline-flex items-center gap-2 px-3.5 py-1.5 rounded-full bg-violet-100/90 border border-violet-200 backdrop-blur-xs">
              <span className="flex h-2 w-2 rounded-full bg-amber-600 animate-pulse" />
              <span className="text-[11px] sm:text-xs font-sans font-bold text-violet-950 tracking-wide">
                CAPSTONE INITIATIVE • TEAM {PROJECT_INFO.teamCode}
              </span>
            </div>

            {/* Serif Main Headline */}
            <h1 className="text-3xl sm:text-5xl lg:text-6xl font-serif font-extrabold tracking-tight text-slate-900 leading-[1.15]">
              Master Independent <br />
              <span className="text-gradient-violet italic font-normal">Choral Sight-Reading</span> <br />
              with <span className="text-violet-900">VoxSight</span>
            </h1>

            {/* Subtitle */}
            <p className="text-sm sm:text-base lg:text-lg text-slate-600 font-sans leading-relaxed max-w-2xl mx-auto lg:mx-0">
              An Android mobile companion created for amateur choir members. Scans printed choir sheet music into digital scores, isolates your vocal part with staff dimming, plays sustained vocal soundfonts, and gives real-time microphone pitch feedback.
            </p>

            {/* Action Buttons: Original Content with Responsive Flex Stacking */}
            <div className="flex flex-col sm:flex-row items-stretch sm:items-center justify-center lg:justify-start gap-3 pt-2">
              <a
                href="#download"
                className="inline-flex items-center justify-center gap-2.5 px-7 py-3.5 text-xs sm:text-sm font-sans font-bold text-white bg-gradient-to-r from-violet-900 via-violet-800 to-indigo-800 hover:from-violet-950 hover:to-indigo-900 rounded-2xl shadow-lg shadow-violet-900/15 hover:-translate-y-0.5 transition-all duration-200 min-h-[48px]"
              >
                <Download className="w-4 h-4 sm:w-5 sm:h-5 text-amber-300" />
                <span>Download Android APK</span>
                <span className="text-[11px] font-mono opacity-80">({PROJECT_INFO.apkSize})</span>
              </a>

              <a
                href="#features"
                className="inline-flex items-center justify-center gap-2 px-6 py-3.5 text-xs sm:text-sm font-sans font-semibold text-slate-700 hover:text-violet-900 bg-[#F4EFEA] hover:bg-[#EBE3DB] border border-[#E3D7CB] rounded-2xl transition-all duration-200 min-h-[48px]"
              >
                <span>Explore Core Features</span>
                <ArrowRight className="w-4 h-4 text-amber-700" />
              </a>
            </div>

            {/* System Metrics Grid: 2 cols on mobile, 4 cols on tablet/desktop */}
            <div className="pt-4 sm:pt-6 grid grid-cols-2 sm:grid-cols-4 gap-2.5 sm:gap-3 border-t border-[#EFE5DB]">
              <div className="p-2.5 sm:p-3 rounded-xl bg-white border border-[#E9DFD5] shadow-2xs text-left">
                <div className="text-[10px] sm:text-[11px] text-slate-500 font-sans font-medium truncate">OMR Scan Speed</div>
                <div className="text-xs sm:text-base font-mono font-bold text-slate-900">≤10s / page</div>
              </div>
              <div className="p-2.5 sm:p-3 rounded-xl bg-white border border-[#E9DFD5] shadow-2xs text-left">
                <div className="text-[10px] sm:text-[11px] text-slate-500 font-sans font-medium truncate">Staff Dimming</div>
                <div className="text-xs sm:text-base font-mono font-bold text-violet-900">≤20% Opacity</div>
              </div>
              <div className="p-2.5 sm:p-3 rounded-xl bg-white border border-[#E9DFD5] shadow-2xs text-left">
                <div className="text-[10px] sm:text-[11px] text-slate-500 font-sans font-medium truncate">Playhead Sync</div>
                <div className="text-xs sm:text-base font-mono font-bold text-emerald-800">≤0.1s Latency</div>
              </div>
              <div className="p-2.5 sm:p-3 rounded-xl bg-white border border-[#E9DFD5] shadow-2xs text-left">
                <div className="text-[10px] sm:text-[11px] text-slate-500 font-sans font-medium truncate">Pitch Match</div>
                <div className="text-xs sm:text-base font-mono font-bold text-amber-800">≥85% Accuracy</div>
              </div>
            </div>

          </div>

          {/* Right Column: Grounded Phone Mockup over Printed Sheet Music Backdrop */}
          <div className="lg:col-span-5 relative flex justify-center px-2 sm:px-0">
            
            {/* Layer 1: Stylized Printed Sheet Music Backdrop Card with safe clipping */}
            <div className="hidden sm:flex absolute inset-0 bg-[#FAF3E8] border border-[#E5D7C5] rounded-3xl transform rotate-2 shadow-xl flex-col justify-between p-6 overflow-hidden pointer-events-none">
              <div className="opacity-15 font-serif text-[10px] text-slate-800 space-y-1 leading-none select-none">
                <div>SANCTUS IN D MINOR — CHORAL SCORE</div>
                <div className="h-0.5 bg-slate-800 w-full" />
                <div className="h-0.5 bg-slate-800 w-full" />
                <div className="h-0.5 bg-slate-800 w-full" />
                <div className="h-0.5 bg-slate-800 w-full" />
                <div className="h-0.5 bg-slate-800 w-full" />
              </div>
              <div className="opacity-15 font-serif text-[10px] text-slate-800 space-y-1 leading-none select-none">
                <div className="h-0.5 bg-slate-800 w-full" />
                <div className="h-0.5 bg-slate-800 w-full" />
                <div className="h-0.5 bg-slate-800 w-full" />
                <div className="h-0.5 bg-slate-800 w-full" />
                <div className="h-0.5 bg-slate-800 w-full" />
              </div>
            </div>

            {/* Warm Gold Backdrop Ring */}
            <div className="absolute inset-0 bg-gradient-to-tr from-amber-400/20 via-violet-400/15 to-indigo-500/15 rounded-3xl blur-xl" />

            {/* Layer 2: Android Device Frame with Fluid Max-Width */}
            <div className="relative z-10 w-full max-w-[310px] sm:max-w-[340px] bg-slate-900 border-[5px] sm:border-[6px] border-slate-800 rounded-[38px] sm:rounded-[42px] p-3 shadow-2xl shadow-violet-950/20">
              
              {/* Phone Speaker Notch */}
              <div className="w-20 sm:w-24 h-3.5 sm:h-4 bg-slate-800 rounded-full mx-auto mb-2.5 sm:mb-3 flex items-center justify-center gap-2">
                <div className="w-2 h-2 rounded-full bg-slate-600" />
                <div className="w-7 sm:w-8 h-1 rounded-full bg-slate-700" />
              </div>

              {/* App Screen Display */}
              <div className="bg-white rounded-[28px] sm:rounded-[32px] p-3.5 sm:p-4 text-left overflow-hidden border border-slate-200 space-y-3 shadow-inner">
                
                {/* App Bar */}
                <div className="flex items-center justify-between border-b border-slate-100 pb-2">
                  <div className="flex items-center gap-2 min-w-0">
                    <div className="w-6 h-6 sm:w-7 sm:h-7 rounded-lg bg-violet-900 flex items-center justify-center text-amber-300 shrink-0">
                      <Music className="w-3.5 h-3.5" />
                    </div>
                    <div className="truncate">
                      <div className="text-xs font-bold text-slate-900 font-sans truncate">VoxSight Practice</div>
                      <div className="text-[10px] text-violet-900 font-mono font-bold truncate">Tenor Line • Isolated</div>
                    </div>
                  </div>
                  <span className="px-2 py-0.5 text-[8px] sm:text-[9px] font-mono font-bold bg-emerald-100 text-emerald-900 rounded-full border border-emerald-300 shrink-0">
                    MIC ACTIVE
                  </span>
                </div>

                {/* Score Staff Preview */}
                <div className="bg-[#FAF8F5] rounded-xl p-2.5 sm:p-3 border border-slate-200 space-y-2">
                  <div className="flex justify-between items-center text-[9px] sm:text-[10px] text-slate-500 font-sans">
                    <span className="font-medium truncate">"Sanctus in D Minor"</span>
                    <span className="text-violet-900 font-mono font-bold shrink-0">BPM: 92</span>
                  </div>

                  {/* 4 SATB Staves Visualizer */}
                  <div className="space-y-1.5 py-0.5 font-mono text-[9px] sm:text-[10px]">
                    {/* Soprano Staff (Dimmed) */}
                    <div className="opacity-20 flex items-center gap-2">
                      <span className="w-3 text-slate-600 font-bold">S</span>
                      <div className="flex-1 h-0.5 bg-slate-400 relative">
                        <div className="absolute top-1/2 left-1/4 -translate-y-1/2 w-1.5 h-1.5 rounded-full bg-slate-500" />
                      </div>
                    </div>

                    {/* Alto Staff (Dimmed) */}
                    <div className="opacity-20 flex items-center gap-2">
                      <span className="w-3 text-slate-600 font-bold">A</span>
                      <div className="flex-1 h-0.5 bg-slate-400 relative">
                        <div className="absolute top-1/2 left-1/3 -translate-y-1/2 w-1.5 h-1.5 rounded-full bg-slate-500" />
                      </div>
                    </div>

                    {/* Tenor Staff (ACTIVE 100% Opacity) */}
                    <div className="opacity-100 bg-violet-100/90 p-1 rounded-lg border border-violet-300 flex items-center gap-2 shadow-xs">
                      <span className="w-3 text-violet-950 font-bold">T</span>
                      <div className="flex-1 h-0.5 bg-violet-700 relative">
                        <div className="absolute top-1/2 left-1/2 -translate-y-1/2 w-2.5 h-2.5 rounded-full bg-amber-500 shadow-md shadow-amber-500/80 animate-ping" />
                        <div className="absolute top-1/2 left-1/2 -translate-y-1/2 w-2.5 h-2.5 rounded-full bg-amber-500 shadow-md shadow-amber-500/80" />
                      </div>
                      <span className="text-[8px] sm:text-[9px] text-amber-950 font-bold px-1.5 py-0.5 rounded bg-amber-200 border border-amber-300 shrink-0">
                        A3 (220 Hz)
                      </span>
                    </div>

                    {/* Bass Staff (Dimmed) */}
                    <div className="opacity-20 flex items-center gap-2">
                      <span className="w-3 text-slate-600 font-bold">B</span>
                      <div className="flex-1 h-0.5 bg-slate-400 relative">
                        <div className="absolute top-1/2 left-1/2 -translate-y-1/2 w-1.5 h-1.5 rounded-full bg-slate-500" />
                      </div>
                    </div>
                  </div>
                </div>

                {/* Real-Time Pitch Meter */}
                <div className="bg-[#FAF8F5] rounded-xl p-2.5 sm:p-3 border border-slate-200 space-y-1.5">
                  <div className="flex items-center justify-between text-[11px] sm:text-xs font-sans">
                    <span className="text-slate-800 font-semibold flex items-center gap-1">
                      <Mic className="w-3 h-3 text-emerald-700" /> Vocal Input
                    </span>
                    <span className="text-emerald-800 font-bold font-mono text-[10px] sm:text-[11px]">
                      IN TUNE (+2c)
                    </span>
                  </div>

                  {/* Meter Bar */}
                  <div className="relative w-full h-2.5 sm:h-3 bg-slate-200 rounded-full overflow-hidden border border-slate-300">
                    <div className="absolute left-1/2 -translate-x-1/2 w-7 sm:w-8 h-full bg-emerald-200" />
                    <div className="absolute left-[52%] top-0 bottom-0 w-1.5 bg-emerald-600 rounded-full shadow" />
                  </div>

                  <div className="flex justify-between text-[8px] sm:text-[9px] text-slate-500 font-mono">
                    <span>FLAT (-50c)</span>
                    <span className="text-emerald-800 font-bold">TARGET</span>
                    <span>SHARP (+50c)</span>
                  </div>
                </div>

                {/* Vocal Soundfont Indicator */}
                <div className="p-2 rounded-xl bg-violet-50 border border-violet-200 flex items-center justify-between font-sans">
                  <div className="flex items-center gap-1.5">
                    <div className="flex items-end gap-0.5 h-3">
                      <span className="w-1 bg-violet-700 rounded-full h-3" />
                      <span className="w-1 bg-emerald-600 rounded-full h-2" />
                      <span className="w-1 bg-violet-700 rounded-full h-2.5" />
                    </div>
                    <span className="text-[9px] sm:text-[10px] text-violet-950 font-bold">
                      "Aah" Vocal Soundfont
                    </span>
                  </div>
                  <span className="text-[9px] font-mono text-slate-500">≤0.1s Sync</span>
                </div>

              </div>

            </div>
          </div>

        </div>
      </div>
    </section>
  );
};
