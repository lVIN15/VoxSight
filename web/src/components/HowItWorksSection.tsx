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
    <section id="how-it-works" className="py-16 sm:py-20 md:py-28 relative bg-[#FAF8F5] overflow-hidden">
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

          <p className="text-sm sm:text-base font-sans text-slate-600 leading-relaxed max-w-2xl">
            Follow the score path from printed sheet music scan to real-time vocal intonation feedback.
          </p>
        </div>

        {/* Workflow Steps Grid: Intelligent Adaptive Layout across Mobile, Tablet, and Desktop */}
        <div className="relative">
          
          {/* Desktop Horizontal Line */}
          <div className="hidden lg:block absolute left-[8%] right-[8%] top-7 h-[2.5px] bg-gradient-to-r from-violet-400 via-violet-600 to-indigo-500 pointer-events-none z-0" />

          {/* Mobile Vertical Line */}
          <div className="sm:hidden absolute left-[27px] top-7 bottom-7 w-[2px] bg-violet-200 pointer-events-none z-0" />

          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-5 gap-6 sm:gap-8 lg:gap-6 relative z-10">
            {steps.map((step) => {
              const Icon = step.icon;
              return (
                <div 
                  key={step.num} 
                  className="flex flex-row sm:flex-col items-start sm:items-center space-x-4 sm:space-x-0 space-y-0 sm:space-y-4 text-left sm:text-center group bg-white sm:bg-transparent p-4 sm:p-0 rounded-2xl sm:rounded-none border sm:border-0 border-[#EBE2D5] shadow-2xs sm:shadow-none"
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
                  <div className="flex-1 space-y-2 pt-0.5 sm:pt-0 min-w-0">
                    <div>
                      <span className="font-mono text-xs font-bold text-amber-900 bg-amber-100/90 px-2.5 py-0.5 rounded border border-amber-300/80 shadow-2xs inline-block mb-1.5">
                        Step {step.num}
                      </span>
                      <h3 className="text-base sm:text-lg lg:text-xl font-serif font-bold text-slate-900 group-hover:text-violet-900 transition-colors">
                        {step.title}
                      </h3>
                    </div>

                    <p className="text-xs font-sans text-slate-600 leading-relaxed">
                      {step.desc}
                    </p>

                    <div className="text-[11px] font-mono text-violet-900 bg-violet-50/90 p-2.5 rounded-xl border border-violet-200/80">
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
