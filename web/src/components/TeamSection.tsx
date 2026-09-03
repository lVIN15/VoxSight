import React from 'react';
import { TEAM_MEMBERS, PROJECT_INFO } from '../data/mockData';
import { GraduationCap, Mail, Award } from 'lucide-react';

const GithubIcon: React.FC<{ className?: string }> = ({ className = "w-4 h-4" }) => (
  <svg className={className} fill="currentColor" viewBox="0 0 24 24" aria-hidden="true">
    <path fillRule="evenodd" d="M12 2C6.477 2 2 6.484 2 12.017c0 4.425 2.865 8.18 6.839 9.504.5.092.682-.217.682-.483 0-.237-.008-.868-.013-1.703-2.782.605-3.369-1.343-3.369-1.343-.454-1.158-1.11-1.466-1.11-1.466-.908-.62.069-.608.069-.608 1.003.07 1.53 1.032 1.53 1.032.892 1.53 2.341 1.088 2.91.832.092-.647.35-1.088.636-1.338-2.22-.253-4.555-1.113-4.555-4.951 0-1.093.39-1.988 1.029-2.688-.103-.253-.446-1.272.098-2.65 0 0 .84-.27 2.75 1.026A9.564 9.564 0 0112 6.844c.85.004 1.705.115 2.504.337 1.909-1.296 2.747-1.027 2.747-1.027.546 1.379.202 2.398.1 2.651.64.7 1.028 1.595 1.028 2.688 0 3.848-2.339 4.695-4.566 4.943.359.309.678.92.678 1.855 0 1.338-.012 2.419-.012 2.747 0 .268.18.58.688.482A10.019 10.019 0 0022 12.017C22 6.484 17.522 2 12 2z" clipRule="evenodd" />
  </svg>
);

const LinkedinIcon: React.FC<{ className?: string }> = ({ className = "w-4 h-4" }) => (
  <svg className={className} fill="currentColor" viewBox="0 0 24 24" aria-hidden="true">
    <path d="M19 3a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h14m-.5 15.5v-5.3a3.26 3.26 0 0 0-3.26-3.26c-.85 0-1.84.52-2.28 1.3v-1.11h-2.79v8.37h2.79v-4.93c0-.77.62-1.4 1.39-1.4a1.4 1.4 0 0 1 1.4 1.4v4.93h2.75M6.46 10.9v8.37H9.25V10.9H6.46M7.86 6.75a1.45 1.45 0 1 0 0 2.9 1.45 1.45 0 0 0 0-2.9z" />
  </svg>
);

export const TeamSection: React.FC = () => {
  return (
    <section id="team" className="py-16 sm:py-20 md:py-28 relative bg-[#F7F4EE] border-b border-[#EBE3D7] overflow-hidden">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        
        {/* Header */}
        <div className="max-w-3xl space-y-4 mb-12 sm:mb-16 text-left">
          <div className="inline-flex items-center gap-2 px-3.5 py-1.5 rounded-full bg-violet-100 border border-violet-200">
            <GraduationCap className="w-3.5 h-3.5 text-violet-800" />
            <span className="text-xs font-sans font-bold text-violet-950 tracking-wider">
              CAPSTONE PROJECT • TEAM {PROJECT_INFO.teamCode}
            </span>
          </div>

          <h2 className="text-3xl sm:text-4xl lg:text-5xl font-serif font-extrabold text-slate-900 tracking-tight leading-tight">
            VoxSight <span className="text-gradient-violet italic font-normal">Development Team</span>
          </h2>

          <p className="text-sm sm:text-base font-sans text-slate-600 leading-relaxed">
            VoxSight was conceptualized, designed, and engineered as an academic capstone software project.
          </p>
        </div>

        {/* Capstone Info Banner */}
        <div className="bg-white rounded-2xl p-5 sm:p-6 border border-[#EBE2D5] shadow-xs mb-10 sm:mb-12 flex flex-col sm:flex-row items-center justify-between gap-4 text-center sm:text-left">
          <div className="flex items-center gap-3 sm:gap-4">
            <div className="w-11 h-11 sm:w-12 sm:h-12 rounded-xl bg-violet-100 border border-violet-200 flex items-center justify-center text-violet-900 shrink-0">
              <Award className="w-5 h-5 sm:w-6 sm:h-6" />
            </div>
            <div>
              <div className="text-[11px] sm:text-xs font-mono font-bold text-violet-900">UNDERGRADUATE CAPSTONE INITIATIVE</div>
              <div className="text-base sm:text-lg font-serif font-bold text-slate-900">Bachelor of Science in Information Technology</div>
            </div>
          </div>

          <div className="inline-flex items-center gap-2 px-3.5 py-2 rounded-xl bg-amber-100/80 border border-amber-300 text-xs font-mono text-amber-950 font-bold shrink-0">
            <span>Team Code:</span>
            <span>{PROJECT_INFO.teamCode}</span>
          </div>
        </div>

        {/* Team Member Cards Grid */}
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-5 sm:gap-6">
          {TEAM_MEMBERS.map((member, index) => (
            <div
              key={member.name}
              className="bg-white rounded-2xl p-5 sm:p-6 border border-[#EBE2D5] shadow-xs hover:shadow-md hover:border-violet-300 transition-all flex flex-col justify-between text-left"
            >
              <div>
                <div className="flex items-center justify-between mb-4">
                  <div className="w-11 h-11 sm:w-12 sm:h-12 rounded-full bg-gradient-to-tr from-violet-900 to-indigo-700 p-0.5 shadow-xs">
                    <div className="w-full h-full bg-white rounded-full flex items-center justify-center font-serif font-bold text-violet-950 text-sm">
                      {member.name.split(' ')[0][0]}
                      {member.name.split(' ').slice(-1)[0][0]}
                    </div>
                  </div>
                  <span className="text-[10px] font-mono font-bold text-violet-950 bg-violet-50 px-2.5 py-1 rounded border border-violet-200">
                    Member #{index + 1}
                  </span>
                </div>

                <h3 className="text-base sm:text-lg font-serif font-bold text-slate-900 mb-1">{member.name}</h3>
                <div className="text-xs font-sans font-bold text-violet-800 mb-2 leading-snug">{member.role}</div>
                <div className="text-xs font-sans text-slate-500">{member.degree}</div>
              </div>

              {/* Contact Icons with Minimum 40px Touch Targets */}
              <div className="pt-4 border-t border-[#F3EAE0] mt-4 flex items-center gap-2.5">
                <a
                  href={member.githubUrl || '#'}
                  className="w-9 h-9 rounded-lg bg-slate-100 hover:bg-violet-100 text-slate-600 hover:text-violet-900 transition-colors flex items-center justify-center"
                  aria-label="GitHub Profile"
                >
                  <GithubIcon className="w-4 h-4" />
                </a>
                <a
                  href={member.linkedinUrl || '#'}
                  className="w-9 h-9 rounded-lg bg-slate-100 hover:bg-violet-100 text-slate-600 hover:text-violet-900 transition-colors flex items-center justify-center"
                  aria-label="LinkedIn Profile"
                >
                  <LinkedinIcon className="w-4 h-4" />
                </a>
                <a
                  href={`mailto:${member.email || 'info@voxsight.org'}`}
                  className="w-9 h-9 rounded-lg bg-slate-100 hover:bg-violet-100 text-slate-600 hover:text-violet-900 transition-colors flex items-center justify-center"
                  aria-label="Send Email"
                >
                  <Mail className="w-4 h-4" />
                </a>
              </div>
            </div>
          ))}
        </div>

      </div>
    </section>
  );
};
