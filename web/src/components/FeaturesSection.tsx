import React from 'react';
import { CORE_FEATURES } from '../data/mockData';
import { Check, Sparkles } from 'lucide-react';
import { TrebleClefIcon, StaffIsolationIcon, WaveformIcon, TuningForkIcon } from './icons/CustomMusicIcons';
import { InteractiveSATBDemo } from './InteractiveSATBDemo';

export const FeaturesSection: React.FC = () => {
  const getCustomIcon = (iconName: string) => {
    switch (iconName) {
      case 'ScanLine':
        return TrebleClefIcon;
      case 'Eye':
        return StaffIsolationIcon;
      case 'Activity':
        return WaveformIcon;
      case 'Mic':
        return TuningForkIcon;
      default:
        return TrebleClefIcon;
    }
  };

  return (
    <section id="features" className="py-20 md:py-28 relative bg-[#F7F4EE] border-b border-[#EBE3D7]">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        
        {/* Header */}
        <div className="max-w-3xl space-y-4 mb-16 text-left">
          <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-violet-100 border border-violet-200">
            <Sparkles className="w-3.5 h-3.5 text-violet-800" />
            <span className="text-xs font-sans font-bold text-violet-950 tracking-wider">
              SYSTEM CAPABILITIES
            </span>
          </div>

          <h2 className="text-3xl sm:text-4xl lg:text-5xl font-serif font-extrabold text-slate-900 tracking-tight leading-tight">
            Designed for <br />
            <span className="text-gradient-violet italic font-normal">Independent Practice Excellence</span>
          </h2>

          <p className="text-base font-sans text-slate-600 leading-relaxed">
            VoxSight incorporates four synchronized core modules specifically engineered to solve independent sight-reading challenges.
          </p>
        </div>

        {/* Feature Cards Grid with Custom Music Icons */}
        <div className="grid grid-cols-1 md:grid-cols-2 gap-8 mb-12">
          {CORE_FEATURES.map((feature) => {
            const CustomIcon = getCustomIcon(feature.iconName);
            return (
              <div
                key={feature.id}
                className="bg-white rounded-3xl p-8 border border-[#EBE2D5] shadow-md shadow-violet-900/5 hover:shadow-xl hover:border-violet-300 transition-all flex flex-col justify-between relative overflow-hidden"
              >
                <div className="space-y-6 relative z-10">
                  {/* Icon & Badge */}
                  <div className="flex items-center justify-between">
                    <div className="w-14 h-14 rounded-2xl bg-violet-100/80 border border-violet-200 flex items-center justify-center text-violet-900 shadow-sm">
                      <CustomIcon className="w-7 h-7" />
                    </div>

                    <div className="flex flex-col items-end">
                      <span className="px-3 py-1 text-xs font-mono font-bold bg-amber-100/80 text-amber-900 rounded-full border border-amber-300">
                        {feature.badge}
                      </span>
                      <span className="text-xs font-mono font-bold text-emerald-800 mt-1">
                        {feature.metric}
                      </span>
                    </div>
                  </div>

                  {/* Title & Description */}
                  <div>
                    <h3 className="text-2xl font-serif font-bold text-slate-900 mb-2">{feature.title}</h3>
                    <p className="text-sm font-sans text-slate-600 leading-relaxed">{feature.fullDesc}</p>
                  </div>

                  {/* Bullet Highlights */}
                  <ul className="space-y-2.5 pt-4 border-t border-[#F3EAE0]">
                    {feature.highlights.map((highlight, idx) => (
                      <li key={idx} className="flex items-start gap-2.5 text-xs font-sans text-slate-700">
                        <Check className="w-4 h-4 text-emerald-700 shrink-0 mt-0.5" />
                        <span>{highlight}</span>
                      </li>
                    ))}
                  </ul>
                </div>
              </div>
            );
          })}
        </div>

        {/* Embedded Interactive SATB Simulator */}
        <InteractiveSATBDemo />

      </div>
    </section>
  );
};
