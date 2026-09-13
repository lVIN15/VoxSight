import React from 'react';
import { Play, ExternalLink, Download } from 'lucide-react';
import { PROJECT_INFO } from '../data/mockData';

export const VideoDemoSection: React.FC = () => {
  const highlights = [
    {
      title: 'Real-Time Camera Scan',
      desc: 'Automatic leveling guide, flashlight toggle, and multi-page capture for printed SATB choir scores.',
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
      desc: 'Real-time microphone intonation analysis (cents deviation) gives singers immediate visual feedback.',
    },
  ];

  return (
    <section id="demo" className="py-16 sm:py-20 md:py-28 relative bg-[#F7F4EE] border-t border-b border-[#EBE3D7] overflow-hidden">
      {/* Background soft glow */}
      <div className="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 w-[400px] sm:w-[650px] h-[350px] sm:h-[500px] bg-gradient-to-tr from-violet-200/40 via-purple-100/30 to-amber-100/40 blur-[100px] sm:blur-[140px] rounded-full pointer-events-none" />

      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 relative z-10">
        
        {/* Section Header */}
        <div className="max-w-3xl space-y-4 mb-10 sm:mb-14 text-left">
          <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-violet-100 border border-violet-200">
            <Play className="w-3.5 h-3.5 text-violet-800 fill-violet-800" />
            <span className="text-xs font-sans font-bold text-violet-950 tracking-wider">
              SYSTEM DEMONSTRATION
            </span>
          </div>

          <h2 className="text-3xl sm:text-4xl lg:text-5xl font-serif font-extrabold text-slate-900 tracking-tight leading-tight">
            See VoxSight <br />
            <span className="text-gradient-violet italic font-normal">in Live Action</span>
          </h2>

          <p className="text-sm sm:text-base font-sans text-slate-600 leading-relaxed">
            Watch an end-to-end demonstration of the VoxSight companion application — capturing physical sheet music, isolating vocal harmonies, and singing with real-time pitch feedback.
          </p>
        </div>

        {/* Video Player Card */}
        <div className="relative rounded-2xl sm:rounded-3xl p-2.5 sm:p-4 bg-white/80 backdrop-blur-md border border-[#E5D7C5] shadow-xl shadow-violet-950/10 mb-10 sm:mb-12">
          <div className="relative w-full aspect-video rounded-xl sm:rounded-2xl overflow-hidden bg-slate-950 shadow-inner">
            <iframe
              src="https://www.youtube-nocookie.com/embed/31P9AGV1Fak?rel=0&modestbranding=1"
              title="VoxSight Mobile Application Live Demo"
              allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share"
              allowFullScreen
              className="absolute top-0 left-0 w-full h-full border-0"
            />
          </div>

          {/* Quick Player Bar */}
          <div className="mt-3.5 sm:mt-4 px-2 sm:px-3 flex flex-col sm:flex-row items-start sm:items-center justify-between gap-3 text-xs text-slate-500 font-sans">
            <div className="flex items-center gap-2">
              <span className="flex h-2 w-2 rounded-full bg-emerald-500" />
              <span className="font-medium text-slate-700">Official Capstone System Walkthrough</span>
              <span className="hidden md:inline text-slate-400">• High-Definition Video</span>
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
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-3.5 sm:gap-4">
          {highlights.map((item, idx) => (
            <div
              key={idx}
              className="p-4 sm:p-5 rounded-xl sm:rounded-2xl bg-white/70 border border-[#EADFD5] shadow-2xs text-left hover:bg-white transition-all"
            >
              <div className="flex items-center gap-2 mb-2">
                <span className="flex items-center justify-center w-5 h-5 rounded-full bg-violet-100 text-violet-900 font-mono text-xs font-bold">
                  {idx + 1}
                </span>
                <h3 className="text-sm font-sans font-bold text-slate-900">{item.title}</h3>
              </div>
              <p className="text-xs text-slate-600 font-sans leading-relaxed">
                {item.desc}
              </p>
            </div>
          ))}
        </div>

        {/* Call to action underneath */}
        <div className="mt-8 sm:mt-10 pt-6 border-t border-[#E8DEC8] flex flex-col sm:flex-row items-center justify-between gap-4">
          <div className="text-left">
            <div className="text-xs sm:text-sm font-bold text-slate-800">
              Ready to test VoxSight on your device?
            </div>
            <div className="text-xs text-slate-500">
              Download the release v{PROJECT_INFO.apkVersion} APK build and start sight-reading today.
            </div>
          </div>
          <a
            href="#download"
            className="inline-flex items-center gap-2 px-5 py-2.5 rounded-xl bg-violet-900 hover:bg-violet-950 text-white text-xs font-bold transition-all shadow-md shadow-violet-900/15"
          >
            <Download className="w-3.5 h-3.5 text-amber-300" />
            <span>Get Android APK</span>
          </a>
        </div>

      </div>
    </section>
  );
};
