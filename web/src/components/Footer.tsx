import React from 'react';
import { PROJECT_INFO } from '../data/mockData';
import { Music, ArrowUp } from 'lucide-react';

export const Footer: React.FC = () => {
  const scrollToTop = () => {
    window.scrollTo({ top: 0, behavior: 'smooth' });
  };

  return (
    <footer className="bg-slate-950 border-t border-slate-800 pt-16 pb-12 text-slate-400 text-xs font-sans">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 space-y-12">
        
        {/* Top Grid */}
        <div className="grid grid-cols-1 md:grid-cols-12 gap-8 items-start">
          
          {/* Logo & Info */}
          <div className="md:col-span-5 space-y-4 text-left">
            <a href="#" className="flex items-center gap-3">
              <div className="w-9 h-9 rounded-xl bg-gradient-to-tr from-violet-700 to-indigo-600 p-0.5 shadow-md">
                <div className="w-full h-full bg-slate-900 rounded-[10px] flex items-center justify-center">
                  <Music className="w-4 h-4 text-amber-300" />
                </div>
              </div>
              <span className="text-2xl font-serif font-bold text-white tracking-tight">VoxSight</span>
            </a>

            <p className="text-slate-400 text-xs leading-relaxed max-w-sm">
              {PROJECT_INFO.subtitle}. Developed as an academic capstone software project for independent choral sight-reading practice.
            </p>

            <div className="inline-flex items-center gap-2 px-3 py-1 rounded bg-slate-900 border border-slate-800 text-[11px] font-mono text-violet-300">
              <span>Team Code:</span>
              <span className="text-amber-400 font-bold">{PROJECT_INFO.teamCode}</span>
            </div>
          </div>

          {/* Navigation Links */}
          <div className="md:col-span-3 space-y-3 text-left">
            <div className="text-white font-bold text-xs font-mono uppercase tracking-wider">Navigation</div>
            <ul className="space-y-2">
              <li><a href="#about" className="hover:text-white transition-colors">About VoxSight</a></li>
              <li><a href="#problem" className="hover:text-white transition-colors">Choral Literacy Gap</a></li>
              <li><a href="#features" className="hover:text-white transition-colors">System Capabilities</a></li>
              <li><a href="#how-it-works" className="hover:text-white transition-colors">Practice Workflow</a></li>
              <li><a href="#showcase" className="hover:text-white transition-colors">Android Showcase</a></li>
              <li><a href="#tech-stack" className="hover:text-white transition-colors">Technology Stack</a></li>
              <li><a href="#team" className="hover:text-white transition-colors">Capstone Team</a></li>
            </ul>
          </div>

          {/* APK Distribution */}
          <div className="md:col-span-4 space-y-3 text-left">
            <div className="text-white font-bold text-xs font-mono uppercase tracking-wider">APK Distribution</div>
            <p className="text-slate-400 text-xs leading-relaxed">
              VoxSight is distributed directly as a static Android Application Package (APK). Compatible with Android 8.0 (API Level 26) or higher.
            </p>
            <div className="pt-2">
              <a
                href="#download"
                className="inline-flex items-center gap-2 px-4 py-2 rounded-xl bg-violet-900 hover:bg-violet-800 border border-violet-700 text-white font-semibold text-xs transition-colors"
              >
                <span className="text-amber-200">Download APK v{PROJECT_INFO.apkVersion}</span>
              </a>
            </div>
          </div>

        </div>

        {/* Bottom Bar */}
        <div className="pt-8 border-t border-slate-800 flex flex-col sm:flex-row items-center justify-between gap-4">
          <p className="text-[11px] text-slate-500 text-center sm:text-left">
            © {new Date().getFullYear()} VoxSight Project Showcase. Capstone Team {PROJECT_INFO.teamCode}. All rights reserved.
          </p>

          <button
            onClick={scrollToTop}
            className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-slate-900 hover:bg-slate-800 text-slate-300 hover:text-white border border-slate-800 transition-colors text-[11px]"
          >
            <span>Back to top</span>
            <ArrowUp className="w-3.5 h-3.5 text-amber-300" />
          </button>
        </div>

      </div>
    </footer>
  );
};
