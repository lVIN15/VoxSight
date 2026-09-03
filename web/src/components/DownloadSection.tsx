import React from 'react';
import { PROJECT_INFO } from '../data/mockData';
import { Download, ShieldCheck, Smartphone, FileCode } from 'lucide-react';

export const DownloadSection: React.FC = () => {
  return (
    <section id="download" className="py-16 sm:py-20 md:py-28 relative bg-[#FAF8F5] overflow-hidden">
      <div className="max-w-5xl mx-auto px-4 sm:px-6 lg:px-8 relative z-10">
        
        {/* Main Banner Card */}
        <div className="bg-gradient-to-br from-violet-950 via-violet-900 to-indigo-950 rounded-3xl p-6 sm:p-10 md:p-14 text-white text-center shadow-2xl shadow-violet-950/20 relative overflow-hidden">
          
          <div className="max-w-3xl mx-auto space-y-5 sm:space-y-6 relative z-10">
            
            {/* Version Badge */}
            <div className="inline-flex items-center gap-2 px-3.5 py-1.5 rounded-full bg-white/10 border border-white/20">
              <ShieldCheck className="w-4 h-4 text-emerald-400 shrink-0" />
              <span className="text-[11px] sm:text-xs font-bold text-violet-100 font-mono tracking-wider">
                STATIC APK DISTRIBUTION • RELEASE v{PROJECT_INFO.apkVersion}
              </span>
            </div>

            {/* Serif Headline */}
            <h2 className="text-3xl sm:text-4xl lg:text-5xl font-serif font-extrabold text-white tracking-tight leading-tight">
              Start Practicing with <br />
              <span className="text-amber-300 italic font-normal">VoxSight Today</span>
            </h2>

            <p className="text-sm sm:text-base font-sans text-violet-100 leading-relaxed max-w-2xl mx-auto">
              Download the VoxSight Android application directly as a static APK file. Scan printed choir sheet music and get real-time vocal intonation feedback at home.
            </p>

            {/* Download Button */}
            <div className="pt-2 sm:pt-4 flex flex-col items-center justify-center gap-4">
              <a
                href={PROJECT_INFO.downloadUrl}
                download="voxsight-v1.0.0.apk"
                className="group inline-flex items-center justify-center gap-3 px-8 sm:px-9 py-4 text-sm sm:text-base font-sans font-bold text-violet-950 bg-white hover:bg-amber-100 rounded-2xl shadow-xl hover:-translate-y-1 transition-all duration-200 min-h-[52px]"
              >
                <Download className="w-5 h-5 sm:w-6 sm:h-6 text-violet-900 group-hover:animate-bounce" />
                <span>Download Android APK</span>
                <span className="text-xs font-mono opacity-80 text-violet-950">({PROJECT_INFO.apkSize})</span>
              </a>

              <div className="flex flex-wrap items-center justify-center gap-3 sm:gap-4 text-xs text-violet-200 font-mono">
                <span className="flex items-center gap-1.5">
                  <Smartphone className="w-3.5 h-3.5 text-amber-300" /> {PROJECT_INFO.minAndroidVersion}
                </span>
                <span>•</span>
                <span className="flex items-center gap-1.5">
                  <ShieldCheck className="w-3.5 h-3.5 text-emerald-400" /> Verified Clean Build
                </span>
                <span>•</span>
                <span className="flex items-center gap-1.5">
                  <FileCode className="w-3.5 h-3.5 text-amber-300" /> Package: com.voxsight.app
                </span>
              </div>
            </div>

            {/* Android Installation Steps: Adaptive 1 -> 2 -> 4 columns */}
            <div className="pt-8 sm:pt-10 border-t border-white/10 text-left">
              <h3 className="text-xs font-mono font-bold text-violet-200 uppercase tracking-wider mb-4 text-center sm:text-left">
                How to Install the APK on Android
              </h3>

              <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-3.5 sm:gap-4">
                <div className="p-4 rounded-xl bg-white/10 border border-white/10 space-y-1.5">
                  <div className="text-xs font-mono font-bold text-emerald-400">STEP 1</div>
                  <div className="text-xs font-bold text-white font-sans">Download APK</div>
                  <div className="text-[11px] font-sans text-violet-200">Tap the download button from your Android phone browser.</div>
                </div>

                <div className="p-4 rounded-xl bg-white/10 border border-white/10 space-y-1.5">
                  <div className="text-xs font-mono font-bold text-amber-300">STEP 2</div>
                  <div className="text-xs font-bold text-white font-sans">Allow Install</div>
                  <div className="text-[11px] font-sans text-violet-200">Enable "Install from Unknown Sources" if prompted by Android.</div>
                </div>

                <div className="p-4 rounded-xl bg-white/10 border border-white/10 space-y-1.5">
                  <div className="text-xs font-mono font-bold text-indigo-300">STEP 3</div>
                  <div className="text-xs font-bold text-white font-sans">Open File</div>
                  <div className="text-[11px] font-sans text-violet-200">Tap the downloaded <code>voxsight-v1.0.0.apk</code> file to install.</div>
                </div>

                <div className="p-4 rounded-xl bg-white/10 border border-white/10 space-y-1.5">
                  <div className="text-xs font-mono font-bold text-purple-300">STEP 4</div>
                  <div className="text-xs font-bold text-white font-sans">Start Practice</div>
                  <div className="text-[11px] font-sans text-violet-200">Open VoxSight, allow microphone access, and begin practicing!</div>
                </div>
              </div>
            </div>

          </div>

        </div>

      </div>
    </section>
  );
};
