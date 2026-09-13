import React from 'react';
import { Play, ExternalLink, Download } from 'lucide-react';
import { PROJECT_INFO } from '../data/mockData';

export const VideoDemoSection: React.FC = () => {
  const highlights = [
    {
      title: 'Real-Time Camera Scan',
      desc: 'Automatic leveling guide, flashlight toggle, and multi-page capture for printed SATB scores.',
    },
    {
      title: 'OMR MusicXML Generation',
      desc: 'Deep learning pipeline transforms paper notation into clean, interactive digital scores in seconds.',
    },
    {
      title: 'Selective Voice Isolation',
      desc: 'Dims non-selected staves to ≤20% opacity and isolates vocal parts with sustained human soundfonts.',
    },
    {
      title: 'Live Pitch Feedback',
      desc: 'Real-time microphone intonation analysis (cents deviation) gives singers immediate visual guidance.',
    },
  ];

  return (
    <section id="demo" className="py-14 sm:py-18 md:py-24 relative bg-[#F7F4EE] border-t border-b border-[#EBE3D7] overflow-hidden">
      {/* Background soft glow */}
      <div className="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 w-[340px] sm:w-[500px] h-[300px] sm:h-[400px] bg-gradient-to-tr from-violet-200/35 via-purple-100/25 to-amber-100/35 blur-[90px] sm:blur-[120px] rounded-full pointer-events-none" />

      <div className="max-w-5xl mx-auto px-4 sm:px-6 lg:px-8 relative z-10">
        
        {/* Section Header - Centered & Compact */}
        <div className="max-w-2xl mx-auto space-y-3 mb-8 sm:mb-10 text-center">
          <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-violet-100 border border-violet-200">
            <Play className="w-3 h-3 text-violet-800 fill-violet-800" />
            <span className="text-[11px] sm:text-xs font-sans font-bold text-violet-950 tracking-wider">
              SYSTEM DEMONSTRATION
            </span>
          </div>

          <h2 className="text-2xl sm:text-3xl lg:text-4xl font-serif font-extrabold text-slate-900 tracking-tight leading-tight">
            See VoxSight <span className="text-gradient-violet italic font-normal">in Live Action</span>
          </h2>

          <p className="text-xs sm:text-sm font-sans text-slate-600 leading-relaxed max-w-xl mx-auto">
            Watch an end-to-end demonstration of the VoxSight companion application — capturing physical sheet music, isolating vocal harmonies, and singing with real-time pitch feedback.
          </p>
        </div>

        {/* Video Player Card - Constrained Width (max-w-3xl) */}
        <div className="max-w-3xl mx-auto">
          <div className="relative rounded-2xl p-2 sm:p-3 bg-white/90 backdrop-blur-md border border-[#E5D7C5] shadow-lg shadow-violet-950/10 mb-6 sm:mb-8">
            <div className="relative w-full aspect-video rounded-xl overflow-hidden bg-slate-950 shadow-inner">
              <iframe
                src="https://www.youtube-nocookie.com/embed/31P9AGV1Fak?rel=0&modestbranding=1"
                title="VoxSight Mobile Application Live Demo"
                allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share"
                allowFullScreen
                className="absolute top-0 left-0 w-full h-full border-0"
              />
            </div>

            {/* Quick Player Bar */}
            <div className="mt-2.5 sm:mt-3 px-2 flex flex-col sm:flex-row items-start sm:items-center justify-between gap-2 text-[11px] sm:text-xs text-slate-500 font-sans">
              <div className="flex items-center gap-2">
                <span className="flex h-2 w-2 rounded-full bg-emerald-500" />
                <span className="font-medium text-slate-700">Official Capstone System Walkthrough</span>
                <span className="hidden sm:inline text-slate-400">• HD Video</span>
              </div>

              <a
                href="https://www.youtube.com/watch?v=31P9AGV1Fak"
                target="_blank"
                rel="noopener noreferrer"
                className="inline-flex items-center gap-1.5 text-violet-900 hover:text-violet-950 font-bold hover:underline transition-colors"
              >
                <span>Open on YouTube</span>
                <ExternalLink className="w-3.5 h-3.5" />
              </a>
            </div>
          </div>

          {/* Walkthrough Highlights Grid */}
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            {highlights.map((item, idx) => (
              <div
                key={idx}
                className="p-3 sm:p-3.5 rounded-xl bg-white/70 border border-[#EADFD5] shadow-2xs text-left hover:bg-white transition-all"
              >
                <div className="flex items-center gap-2 mb-1">
                  <span className="flex items-center justify-center w-5 h-5 rounded-full bg-violet-100 text-violet-900 font-mono text-[11px] font-bold shrink-0">
                    {idx + 1}
                  </span>
                  <h3 className="text-xs sm:text-sm font-sans font-bold text-slate-900">{item.title}</h3>
                </div>
                <p className="text-[11px] sm:text-xs text-slate-600 font-sans leading-relaxed pl-7">
                  {item.desc}
                </p>
              </div>
            ))}
          </div>

          {/* Call to action underneath */}
          <div className="mt-6 sm:mt-8 pt-5 border-t border-[#E8DEC8] flex flex-col sm:flex-row items-center justify-between gap-3 text-center sm:text-left">
            <div>
              <div className="text-xs sm:text-sm font-bold text-slate-800">
                Ready to test VoxSight on your device?
              </div>
              <div className="text-[11px] sm:text-xs text-slate-500">
                Download the release v{PROJECT_INFO.apkVersion} APK build and start sight-reading today.
              </div>
            </div>
            <a
              href="#download"
              className="inline-flex items-center gap-2 px-4 py-2 rounded-xl bg-violet-900 hover:bg-violet-950 text-white text-xs font-bold transition-all shadow-md shadow-violet-900/15 shrink-0"
            >
              <Download className="w-3.5 h-3.5 text-amber-300" />
              <span>Get Android APK</span>
            </a>
          </div>
        </div>

      </div>
    </section>
  );
};
