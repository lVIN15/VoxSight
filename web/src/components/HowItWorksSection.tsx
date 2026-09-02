import React from 'react';
import { Upload, Eye, Volume2, Mic, BarChart2 } from 'lucide-react';
import { TrebleClefIcon } from './icons/CustomMusicIcons';

export const HowItWorksSection: React.FC = () => {
  const steps = [
    {
      num: '01',
      title: 'Score Upload',
      desc: 'Photograph or import printed SATB sheet music into the system.',
      detail: 'OMR Engine parses notation into MusicXML in ≤10 seconds',
      icon: Upload,
    },
    {
      num: '02',
      title: 'Voice Isolation',
      desc: 'Select your voice part (Soprano, Alto, Tenor, Bass).',
      detail: 'Unassigned staves automatically dim to ≤20% opacity',
      icon: Eye,
    },
    {
      num: '03',
      title: 'Vocal Audio Sync',
      desc: 'Play sustained human vocal soundfonts ("Aah" / "Ooh").',
      detail: 'Visual playhead highlights notes with ≤0.1s latency',
      icon: Volume2,
    },
    {
      num: '04',
      title: 'Live Pitch Feedback',
      desc: 'Sing into your device microphone for instant guidance.',
      detail: 'Detects vocal frequency & displays intonation within ≤0.5s',
      icon: Mic,
    },
    {
      num: '05',
      title: 'Performance Review',
      desc: 'Inspect measure-by-measure intonation statistics.',
      detail: 'Identify problem passages to target before rehearsal',
      icon: BarChart2,
    }
  ];

  return (
    <section id="how-it-works" className="py-20 md:py-28 relative bg-[#FAF8F5]">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        
        {/* Section Header */}
        <div className="max-w-3xl space-y-4 mb-12 sm:mb-16 text-left">
          <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-violet-100 border border-violet-200">
            <TrebleClefIcon className="w-4 h-4 text-violet-800" />
            <span className="text-xs font-sans font-bold text-violet-950 tracking-wider">
              PRACTICE WORKFLOW
            </span>
          </div>

          <h2 className="text-3xl sm:text-4xl lg:text-5xl font-serif font-extrabold text-slate-900 tracking-tight leading-tight">
            How VoxSight Guides Your <br className="hidden sm:inline" />
            <span className="text-gradient-violet italic font-normal">Sight-Reading Journey</span>
          </h2>

          <p className="text-base font-sans text-slate-600 leading-relaxed">
            Follow the score path from printed sheet music scan to real-time vocal intonation feedback.
          </p>
        </div>

        {/* Workflow Steps Grid */}
        <div className="relative">
          
          {/* Desktop Horizontal Line */}
          <div className="hidden md:block absolute left-[10%] right-[10%] top-7 h-[2.5px] bg-gradient-to-r from-violet-400 via-violet-600 to-indigo-500 pointer-events-none z-0" />

          {/* Mobile Vertical Line */}
          <div className="md:hidden absolute left-7 top-7 bottom-7 w-[2px] bg-violet-200 pointer-events-none z-0" />

          <div className="grid grid-cols-1 md:grid-cols-5 gap-8 sm:gap-10 md:gap-8 relative z-10">
            {steps.map((step) => {
              const Icon = step.icon;
              return (
                <div 
                  key={step.num} 
                  className="flex flex-row md:flex-col items-start md:items-center space-x-5 md:space-x-0 space-y-0 md:space-y-4 text-left md:text-center group"
                >
                  {/* Icon Circle */}
                  <div className="relative shrink-0 z-10">
                    <div className="w-14 h-14 rounded-full bg-[#FAF8F5] p-0.5 border-2 border-violet-400 shadow-md group-hover:border-violet-700 group-hover:scale-105 transition-all flex items-center justify-center">
                      <div className="w-full h-full rounded-full bg-gradient-to-tr from-violet-900 to-indigo-700 flex items-center justify-center text-amber-300">
                        <Icon className="w-5 h-5" />
                      </div>
                    </div>
                  </div>

                  {/* Step Content */}
                  <div className="flex-1 space-y-2 pt-1 md:pt-0">
                    <div>
                      <span className="font-mono text-xs font-bold text-amber-900 bg-amber-100/90 px-2.5 py-0.5 rounded border border-amber-300/80 shadow-xs inline-block mb-1.5">
                        Step {step.num}
                      </span>
                      <h3 className="text-lg sm:text-xl font-serif font-bold text-slate-900 group-hover:text-violet-900 transition-colors">
                        {step.title}
                      </h3>
                    </div>

                    <p className="text-xs font-sans text-slate-600 leading-relaxed">
                      {step.desc}
                    </p>

                    <div className="text-[11px] font-mono text-violet-900 bg-violet-50/80 p-2.5 rounded-xl border border-violet-200/80">
                      {step.detail}
                    </div>
                  </div>

                </div>
              );
            })}
          </div>

        </div>

      </div>
    </section>
  );
};
