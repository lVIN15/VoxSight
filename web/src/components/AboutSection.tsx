import React from 'react';
import { BookOpen, Sparkles } from 'lucide-react';

export const AboutSection: React.FC = () => {
  const targetAudience = [
    {
      title: 'Amateur & Community Choristers',
      desc: 'Singers who enjoy choral performance but have limited formal music theory or sight-reading literacy.',
      badge: 'Primary Users'
    },
    {
      title: 'Church Choir Members',
      desc: 'Volunteer vocalists preparing weekly liturgical repertoire independently outside of group rehearsals.',
      badge: 'Weekly Repertoire'
    },
    {
      title: 'Vocal-Only Musicians',
      desc: 'Singers without piano access or instrument skills who need an acoustic pitch reference at home.',
      badge: 'No Piano Needed'
    },
    {
      title: 'Choir Directors & Conductors',
      desc: 'Ensemble leaders who direct choir members to VoxSight for individual note preparation between rehearsals.',
      badge: 'Rehearsal Efficiency'
    }
  ];

  return (
    <section id="about" className="py-20 md:py-28 relative bg-[#FAF8F5]">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        
        {/* Section Header */}
        <div className="max-w-3xl space-y-4 mb-16 text-left">
          <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-violet-100 border border-violet-200">
            <BookOpen className="w-3.5 h-3.5 text-violet-800" />
            <span className="text-xs font-sans font-bold text-violet-950 tracking-wider">
              ABOUT VOXSIGHT
            </span>
          </div>

          <h2 className="text-3xl sm:text-4xl lg:text-5xl font-serif font-extrabold text-slate-900 tracking-tight leading-tight">
            An Intelligent Companion for <br />
            <span className="text-gradient-violet italic font-normal">Choral Practice</span>
          </h2>

          <p className="text-base text-slate-600 font-sans leading-relaxed">
            VoxSight is a standalone Android mobile application developed to help amateur choir members practice printed sheet music independently, build sight-reading confidence, and verify pitch accuracy before ensemble rehearsal.
          </p>
        </div>

        {/* Target Persona Cards Grid */}
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-6 mb-16">
          {targetAudience.map((aud, i) => (
            <div
              key={aud.title}
              className="ivory-card ivory-card-hover rounded-2xl p-6 flex flex-col justify-between"
            >
              <div>
                <div className="flex items-center justify-between mb-4">
                  <span className="w-8 h-8 rounded-lg bg-violet-100 text-violet-950 font-bold font-mono text-xs flex items-center justify-center">
                    0{i + 1}
                  </span>
                  <span className="text-[10px] font-mono font-bold px-2 py-0.5 rounded bg-amber-100/80 text-amber-900 border border-amber-300">
                    {aud.badge}
                  </span>
                </div>
                <h3 className="text-lg font-serif font-bold text-slate-900 mb-2">{aud.title}</h3>
                <p className="text-xs font-sans text-slate-600 leading-relaxed">{aud.desc}</p>
              </div>
            </div>
          ))}
        </div>

        {/* Mission Banner */}
        <div className="bg-gradient-to-r from-violet-950 via-violet-900 to-indigo-950 rounded-3xl p-8 md:p-12 text-white shadow-xl shadow-violet-950/10 relative overflow-hidden text-left">
          <div className="max-w-3xl space-y-4 relative z-10">
            <div className="inline-flex items-center gap-2 text-xs font-mono font-bold text-amber-200 bg-white/10 px-3 py-1 rounded-full border border-white/20">
              <Sparkles className="w-3.5 h-3.5 text-amber-300" /> OUR MISSION
            </div>
            <h3 className="text-2xl sm:text-3xl lg:text-4xl font-serif font-extrabold text-white">
              Bridging the Feedback Disconnect in Choral Software
            </h3>
            <p className="text-sm sm:text-base font-sans text-violet-100 leading-relaxed">
              Existing software tools either support sheet music scanning without pitch assessment, or offer pitch assessment limited to pre-purchased catalogs. VoxSight bridges this tripartite gap by combining Optical Music Recognition (OMR), synthesized vocal soundfonts, and microphone pitch validation into a single mobile application.
            </p>
          </div>
        </div>

      </div>
    </section>
  );
};
