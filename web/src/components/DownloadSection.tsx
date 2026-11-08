import React from 'react';
import { PROJECT_INFO } from '../data/mockData';
import { Download, ShieldCheck, Smartphone, QrCode } from 'lucide-react';

export const DownloadSection: React.FC = () => {
  return (
    <section id="download" className="py-16 sm:py-20 md:py-28 relative bg-[#FAF8F5] overflow-hidden">
      <div className="max-w-5xl mx-auto px-4 sm:px-6 lg:px-8 relative z-10">
        
        {/* Main Banner Card */}
        <div className="bg-gradient-to-br from-violet-950 via-violet-900 to-indigo-950 rounded-3xl p-6 sm:p-10 md:p-14 text-white text-center shadow-2xl shadow-violet-950/20 relative overflow-hidden">
          
          <div className="max-w-4xl mx-auto space-y-8 relative z-10">
            
            {/* Header Block */}
            <div className="space-y-4 max-w-2xl mx-auto">
              <div className="inline-flex items-center gap-2 px-3.5 py-1.5 rounded-full bg-white/10 border border-white/20">
                <ShieldCheck className="w-4 h-4 text-emerald-400 shrink-0" />
                <span className="text-[11px] sm:text-xs font-bold text-violet-100 font-mono tracking-wider">
                  STATIC APK DISTRIBUTION • RELEASE v{PROJECT_INFO.apkVersion}
                </span>
              </div>

              <h2 className="text-3xl sm:text-4xl lg:text-5xl font-serif font-extrabold text-white tracking-tight leading-tight">
                Start Practicing with <br />
                <span className="text-amber-300 italic font-normal">VoxSight Today</span>
              </h2>

              <p className="text-sm sm:text-base font-sans text-violet-100 leading-relaxed">
                Download the VoxSight Android application directly as a verified static APK file. Scan printed choir sheet music, edit notation, and practice with real-time intonation feedback.
              </p>
            </div>

            {/* 2-Column Installation Cards: Left Direct Download / Right QR Code Scan */}
            <div className="grid grid-cols-1 md:grid-cols-2 gap-5 sm:gap-6 items-stretch text-left">
              
              {/* Option A: Direct File Download */}
              <div className="p-6 sm:p-7 rounded-2xl bg-white/10 border border-white/20 backdrop-blur-xs flex flex-col justify-between space-y-6 shadow-lg">
                <div className="space-y-2">
                  <div className="flex items-center gap-2 text-amber-300 font-mono font-bold text-xs">
                    <Smartphone className="w-4 h-4 shrink-0" />
                    <span>OPTION 1 • DIRECT DOWNLOAD</span>
                  </div>
                  <h3 className="text-xl sm:text-2xl font-serif font-bold text-white">
                    Download APK File
                  </h3>
                  <p className="text-xs font-sans text-violet-100 leading-relaxed">
                    Download the installer package directly to your phone or computer to install manually.
                  </p>
                </div>

                <div className="space-y-3 pt-2">
                  <a
                    href={PROJECT_INFO.downloadUrl}
                    download={`voxsight-v${PROJECT_INFO.apkVersion}.apk`}
                    className="w-full group inline-flex items-center justify-center gap-3 px-6 py-4 text-sm sm:text-base font-sans font-bold text-violet-950 bg-white hover:bg-amber-100 rounded-xl shadow-xl hover:-translate-y-0.5 transition-all duration-200 min-h-[52px]"
                  >
                    <Download className="w-5 h-5 text-violet-900 group-hover:animate-bounce shrink-0" />
                    <span>Download Android APK</span>
                    <span className="text-xs font-mono font-bold px-2 py-0.5 rounded-md bg-violet-100 text-violet-900">
                      {PROJECT_INFO.apkSize}
                    </span>
                  </a>

                  <div className="flex flex-wrap items-center gap-2 text-[11px] font-mono text-violet-200">
                    <span className="px-2.5 py-1 rounded-full bg-white/10 border border-white/10 flex items-center gap-1.5">
                      <Smartphone className="w-3 h-3 text-amber-300" />
                      {PROJECT_INFO.minAndroidVersion}
                    </span>
                    <span className="px-2.5 py-1 rounded-full bg-emerald-500/20 text-emerald-300 border border-emerald-500/30 flex items-center gap-1.5">
                      <ShieldCheck className="w-3 h-3 text-emerald-400" />
                      Verified Clean Build
                    </span>
                  </div>
                </div>
              </div>

              {/* Option B: Quick Scan QR Code */}
              <div className="p-6 sm:p-7 rounded-2xl bg-white/10 border border-white/20 backdrop-blur-xs flex flex-col justify-between items-center text-center space-y-4 shadow-lg">
                <div className="space-y-1">
                  <div className="flex items-center justify-center gap-2 text-amber-300 font-mono font-bold text-xs">
                    <QrCode className="w-4 h-4 shrink-0" />
                    <span>OPTION 2 • INSTANT SCAN</span>
                  </div>
                  <h3 className="text-xl sm:text-2xl font-serif font-bold text-white">
                    Scan with Phone Camera
                  </h3>
                  <p className="text-xs font-sans text-violet-100 leading-relaxed max-w-xs mx-auto">
                    Open your Android camera or QR reader to begin downloading immediately.
                  </p>
                </div>

                <div className="bg-white p-3 rounded-2xl shadow-md inline-block">
                  <img
                    src="/voxsight-v1.4.0-qr.svg"
                    alt="Scan QR code to install VoxSight APK"
                    className="w-28 h-28 sm:w-32 sm:h-32 object-contain"
                    loading="lazy"
                  />
                </div>

                <a
                  href="/voxsight-v1.4.0-qr.png"
                  download="voxsight-v1.4.0-qr.png"
                  className="text-xs font-mono text-amber-300 hover:text-white underline inline-flex items-center gap-1"
                >
                  Save QR Code Image (PNG) ↓
                </a>
              </div>

            </div>

            {/* What's New in v1.4.0 - Full Width Banner */}
            <div className="p-5 sm:p-6 rounded-2xl bg-white/10 border border-white/15 text-left backdrop-blur-xs shadow-lg space-y-4">
              <div className="flex flex-wrap items-center justify-between gap-2 pb-3 border-b border-white/15">
                <span className="font-serif font-bold text-amber-300 text-sm sm:text-base flex items-center gap-2">
                  ★ What's New in Release v{PROJECT_INFO.apkVersion}
                </span>
                <div className="flex items-center gap-3 text-xs font-mono">
                  <span className="text-emerald-300 bg-emerald-950/70 px-2.5 py-0.5 rounded-full border border-emerald-500/30 font-bold">
                    Latest Stable Release
                  </span>
                  <a
                    href={PROJECT_INFO.releaseUrl}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="text-amber-300 hover:text-white underline"
                  >
                    Release Notes ↗
                  </a>
                </div>
              </div>

              <div className="grid grid-cols-1 md:grid-cols-3 gap-3.5 sm:gap-4">
                <div className="p-3.5 rounded-xl bg-white/5 border border-white/10 space-y-1">
                  <div className="font-sans font-bold text-white text-xs sm:text-sm flex items-center gap-2">
                    <span className="w-1.5 h-1.5 rounded-full bg-amber-400 shrink-0" />
                    Soundslice Notation Editor
                  </div>
                  <p className="text-[11px] sm:text-xs text-violet-200 leading-relaxed font-sans">
                    Unified bottom editing deck with standard SMuFL glyphs, SATB voice isolation, and note navigation.
                  </p>
                </div>

                <div className="p-3.5 rounded-xl bg-white/5 border border-white/10 space-y-1">
                  <div className="font-sans font-bold text-white text-xs sm:text-sm flex items-center gap-2">
                    <span className="w-1.5 h-1.5 rounded-full bg-amber-400 shrink-0" />
                    Notes, Rests & Curves
                  </div>
                  <p className="text-[11px] sm:text-xs text-violet-200 leading-relaxed font-sans">
                    Insert Whole to 16th notes/rests, accidental modifiers (♯/♭/♮), octave shifts (±8va), and slur/tie phrasing.
                  </p>
                </div>

                <div className="p-3.5 rounded-xl bg-white/5 border border-white/10 space-y-1">
                  <div className="font-sans font-bold text-white text-xs sm:text-sm flex items-center gap-2">
                    <span className="w-1.5 h-1.5 rounded-full bg-amber-400 shrink-0" />
                    Railway Production Cloud
                  </div>
                  <p className="text-[11px] sm:text-xs text-violet-200 leading-relaxed font-sans">
                    Direct connection to live production cloud services with all development popups removed.
                  </p>
                </div>
              </div>
            </div>

            {/* Android Installation Steps: 4 Steps */}
            <div className="pt-6 sm:pt-8 border-t border-white/10 text-left">
              <h3 className="text-xs font-mono font-bold text-violet-200 uppercase tracking-wider mb-4 text-center sm:text-left">
                How to Install the APK on Android
              </h3>

              <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-3.5 sm:gap-4">
                <div className="p-4 rounded-xl bg-white/10 border border-white/10 space-y-1.5">
                  <div className="text-xs font-mono font-bold text-emerald-400">STEP 1</div>
                  <div className="text-xs font-bold text-white font-sans">Download APK</div>
                  <div className="text-[11px] font-sans text-violet-200">Tap download or scan the QR code using your Android camera.</div>
                </div>

                <div className="p-4 rounded-xl bg-white/10 border border-white/10 space-y-1.5">
                  <div className="text-xs font-mono font-bold text-amber-300">STEP 2</div>
                  <div className="text-xs font-bold text-white font-sans">Allow Install</div>
                  <div className="text-[11px] font-sans text-violet-200">Enable "Install from Unknown Sources" if prompted by Android.</div>
                </div>

                <div className="p-4 rounded-xl bg-white/10 border border-white/10 space-y-1.5">
                  <div className="text-xs font-mono font-bold text-indigo-300">STEP 3</div>
                  <div className="text-xs font-bold text-white font-sans">Open File</div>
                  <div className="text-[11px] font-sans text-violet-200">Tap the downloaded <code>voxsight-v{PROJECT_INFO.apkVersion}.apk</code> file to install.</div>
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
