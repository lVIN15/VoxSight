import React from 'react';
import { VolumeX, EyeOff, Layers, CheckCircle2, XCircle } from 'lucide-react';

export const ProblemSection: React.FC = () => {
  const problems = [
    {
      title: 'Rote Piano Note-Pounding',
      desc: 'Conductors are compelled to adopt rote pedagogy—playing each section (Soprano, Alto, Tenor, Bass) repeatedly on the piano while 75% of the ensemble sits idle.',
      icon: VolumeX,
      color: 'text-rose-700',
      bg: 'bg-rose-100/60 border-rose-200'
    },
    {
      title: 'Visual Cognitive Overload',
      desc: 'Scanning dense 4-part SATB scores forces non-fluent readers to scan neighboring staves constantly, causing visual lost-place errors and breakdown of rhythm.',
      icon: EyeOff,
      color: 'text-violet-800',
      bg: 'bg-violet-100/60 border-violet-200'
    },
    {
      title: 'Percussive Tone Mismatch',
      desc: 'Piano MIDI tones decay immediately upon strike, providing flawed acoustic guidance for vocal intonation compared to sustained human vocal vowel soundfonts.',
      icon: Layers,
      color: 'text-amber-800',
      bg: 'bg-amber-100/60 border-amber-200'
    }
  ];

  return (
    <section id="problem" className="py-20 md:py-28 relative bg-[#F7F4EE] border-y border-[#EBE3D7]">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        
        {/* Editorial Section Header */}
        <div className="max-w-3xl space-y-4 mb-16 text-left">
          <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-violet-100 border border-violet-200">
            <span className="text-xs font-sans font-bold text-violet-950 tracking-wider">
              THE CHORAL LITERACY BOTTLENECK
            </span>
          </div>

          <h2 className="text-3xl sm:text-4xl lg:text-5xl font-serif font-extrabold text-slate-900 tracking-tight leading-tight">
            Why Traditional Rehearsal <br />
            <span className="text-rose-700 italic font-normal">Creates Bottlenecks</span>
          </h2>

          <p className="text-base text-slate-600 font-sans leading-relaxed">
            Amateur and community choirs rely heavily on vocalists without formal notation literacy. Without independent practice tools, group rehearsals revert to piano note-teaching instead of artistic expression.
          </p>
        </div>

        {/* 3 Unboxed Feature Cards with Music Cues */}
        <div className="grid grid-cols-1 md:grid-cols-3 gap-8 mb-16">
          {problems.map((prob) => {
            const Icon = prob.icon;
            return (
              <div
                key={prob.title}
                className="bg-white rounded-2xl p-7 border border-[#EBE2D5] shadow-sm hover:shadow-md transition-all"
              >
                <div className={`w-12 h-12 rounded-xl border flex items-center justify-center mb-5 ${prob.bg}`}>
                  <Icon className={`w-6 h-6 ${prob.color}`} />
                </div>
                <h3 className="text-xl font-serif font-bold text-slate-900 mb-2">{prob.title}</h3>
                <p className="text-xs sm:text-sm font-sans text-slate-600 leading-relaxed">{prob.desc}</p>
              </div>
            );
          })}
        </div>

        {/* Traditional vs VoxSight Side-by-Side Comparison Container */}
        <div className="bg-white rounded-3xl p-6 sm:p-10 border border-[#EBE2D5] shadow-lg shadow-violet-900/5">
          <div className="text-left mb-8 border-b border-[#F0E6D8] pb-6">
            <h3 className="text-2xl sm:text-3xl font-serif font-extrabold text-slate-900">
              Traditional Rehearsal vs. <span className="text-violet-900 italic">VoxSight Practice</span>
            </h3>
            <p className="text-xs sm:text-sm font-sans text-slate-600 mt-1">
              Shifting note-learning from group rehearsals to independent home practice.
            </p>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 gap-8">
            
            {/* Traditional Rehearsal */}
            <div className="p-6 rounded-2xl bg-rose-50/60 border border-rose-200 space-y-4 text-left">
              <div className="flex items-center gap-2 text-rose-900 font-sans font-bold text-sm border-b border-rose-200 pb-3">
                <XCircle className="w-5 h-5 text-rose-600 shrink-0" />
                <span>Traditional Rehearsal Model</span>
              </div>
              <ul className="space-y-3 text-xs font-sans text-slate-700">
                <li className="flex items-start gap-2">
                  <span className="text-rose-600 font-bold">✕</span>
                  <span>Conductor plays each vocal part on piano section by section; ensemble stays idle.</span>
                </li>
                <li className="flex items-start gap-2">
                  <span className="text-rose-600 font-bold">✕</span>
                  <span>Home practice relies on informal, unverified recordings or personal keyboards.</span>
                </li>
                <li className="flex items-start gap-2">
                  <span className="text-rose-600 font-bold">✕</span>
                  <span>Non-readers suffer visual cognitive overload scanning dense 4-part SATB staves.</span>
                </li>
                <li className="flex items-start gap-2">
                  <span className="text-rose-600 font-bold">✕</span>
                  <span>Piano tones decay immediately, providing poor guide for sustained vocal tuning.</span>
                </li>
              </ul>
            </div>

            {/* VoxSight Practice */}
            <div className="p-6 rounded-2xl bg-violet-50/70 border border-violet-200 space-y-4 text-left">
              <div className="flex items-center gap-2 text-violet-950 font-sans font-bold text-sm border-b border-violet-200 pb-3">
                <CheckCircle2 className="w-5 h-5 text-emerald-700 shrink-0" />
                <span>VoxSight Independent Companion</span>
              </div>
              <ul className="space-y-3 text-xs font-sans text-slate-800">
                <li className="flex items-start gap-2">
                  <span className="text-emerald-700 font-bold">✓</span>
                  <span>Scans printed choir sheet music into playable digital MusicXML in ≤10 seconds.</span>
                </li>
                <li className="flex items-start gap-2">
                  <span className="text-emerald-700 font-bold">✓</span>
                  <span>Isolates selected voice part; unassigned staves automatically dim to ≤20% opacity.</span>
                </li>
                <li className="flex items-start gap-2">
                  <span className="text-emerald-700 font-bold">✓</span>
                  <span>Synthesizes sustained human vocal soundfonts ("Aah"/"Ooh") for natural tuning.</span>
                </li>
                <li className="flex items-start gap-2">
                  <span className="text-emerald-700 font-bold">✓</span>
                  <span>Microphone captures vocal input and displays immediate visual intonation feedback.</span>
                </li>
              </ul>
            </div>

          </div>
        </div>

      </div>
    </section>
  );
};
