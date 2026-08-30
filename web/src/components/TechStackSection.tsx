import React from 'react';
import { TECH_STACK } from '../data/mockData';
import { Code, Cpu, Smartphone, Music, Radio, FileText, Terminal } from 'lucide-react';

export const TechStackSection: React.FC = () => {
  const getIcon = (iconName: string) => {
    switch (iconName) {
      case 'Smartphone':
        return Smartphone;
      case 'Cpu':
        return Cpu;
      case 'Music':
        return Music;
      case 'Radio':
        return Radio;
      case 'FileText':
        return FileText;
      case 'Code':
        return Code;
      default:
        return Terminal;
    }
  };

  return (
    <section id="tech-stack" className="py-20 md:py-28 relative bg-[#FAF8F5]">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        
        {/* Header */}
        <div className="max-w-3xl space-y-4 mb-16 text-left">
          <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-violet-100 border border-violet-200">
            <Code className="w-3.5 h-3.5 text-violet-800" />
            <span className="text-xs font-sans font-bold text-violet-950 tracking-wider">
              TECHNOLOGY ARCHITECTURE
            </span>
          </div>

          <h2 className="text-3xl sm:text-4xl lg:text-5xl font-serif font-extrabold text-slate-900 tracking-tight leading-tight">
            Engineered for <br />
            <span className="text-gradient-violet italic font-normal">Accuracy & Reliability</span>
          </h2>

          <p className="text-base font-sans text-slate-600 leading-relaxed">
            The VoxSight platform combines native Android development, digital signal processing, and open notation standards.
          </p>
        </div>

        {/* Tech Stack Cards Grid */}
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6 mb-16">
          {TECH_STACK.map((tech) => {
            const Icon = getIcon(tech.icon);
            return (
              <div
                key={tech.name}
                className="ivory-card ivory-card-hover rounded-2xl p-6 flex flex-col justify-between"
              >
                <div>
                  <div className="flex items-center justify-between mb-4">
                    <div className="w-12 h-12 rounded-xl bg-violet-100 border border-violet-200 flex items-center justify-center text-violet-900">
                      <Icon className="w-6 h-6" />
                    </div>
                    <span className="text-[10px] font-mono font-bold px-2.5 py-1 rounded bg-amber-100/80 text-amber-900 border border-amber-300">
                      {tech.badge}
                    </span>
                  </div>

                  <h3 className="text-lg font-serif font-bold text-slate-900 mb-1">{tech.name}</h3>
                  <div className="text-[11px] font-mono font-bold text-violet-900 mb-3">{tech.category}</div>
                  <p className="text-xs font-sans text-slate-600 leading-relaxed">{tech.description}</p>
                </div>
              </div>
            );
          })}
        </div>

        {/* Architecture Specs Box */}
        <div className="bg-white rounded-3xl p-8 border border-[#EBE2D5] grid grid-cols-1 md:grid-cols-3 gap-6 text-left shadow-sm">
          <div className="space-y-2 border-b md:border-b-0 md:border-r border-[#F0E6D8] pb-6 md:pb-0 md:pr-6">
            <div className="text-xs font-mono font-bold text-violet-900">01 / ACOUSTIC SOUNDFONTS</div>
            <div className="text-base font-serif font-bold text-slate-900">Human Vocal Vowel Tones</div>
            <div className="text-xs font-sans text-slate-600">Replaces percussive piano tones with sustained "Aah" / "Ooh" vocal soundfonts for natural pitch tuning.</div>
          </div>

          <div className="space-y-2 border-b md:border-b-0 md:border-r border-[#F0E6D8] pb-6 md:pb-0 md:pr-6">
            <div className="text-xs font-mono font-bold text-emerald-800">02 / NOTATION STANDARD</div>
            <div className="text-base font-serif font-bold text-slate-900">MusicXML Integration</div>
            <div className="text-xs font-sans text-slate-600">Parses scanned physical scores into standard MusicXML format, preserving SATB stave tracks.</div>
          </div>

          <div className="space-y-2">
            <div className="text-xs font-mono font-bold text-amber-800">03 / DSP PITCH VERIFICATION</div>
            <div className="text-base font-serif font-bold text-slate-900">YIN Pitch Detection</div>
            <div className="text-xs font-sans text-slate-600">Measures sung fundamental frequencies from the microphone PCM stream with low-latency feedback.</div>
          </div>
        </div>

      </div>
    </section>
  );
};
