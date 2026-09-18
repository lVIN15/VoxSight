import React, { useState, useEffect } from 'react';
import { Music } from 'lucide-react';
import { PROJECT_INFO } from '../data/mockData';

export const PrivacyPolicy: React.FC = () => {
  const [scrolled, setScrolled] = useState(false);

  useEffect(() => {
    const handleScroll = () => {
      setScrolled(window.scrollY > 20);
    };
    window.addEventListener('scroll', handleScroll);
    return () => window.removeEventListener('scroll', handleScroll);
  }, []);

  return (
    <div className="min-h-screen flex flex-col bg-[#FAF8F5] text-slate-900 selection:bg-amber-300 selection:text-slate-950">
      
      {/* Custom Privacy Policy Navbar */}
      <header
        className={`fixed top-0 left-0 right-0 z-50 transition-all duration-300 ${
          scrolled
            ? 'bg-[#FAF8F5]/95 backdrop-blur-md border-b border-[#F3EAE0] py-2.5 sm:py-3 shadow-sm shadow-violet-900/5'
            : 'bg-transparent py-4 sm:py-5'
        }`}
      >
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
          <div className="flex items-center justify-between">
            {/* Logo */}
            <a href="/" className="flex items-center gap-2.5 sm:gap-3 group min-w-0">
              <div className="w-9 h-9 sm:w-10 sm:h-10 rounded-xl bg-gradient-to-tr from-violet-900 via-violet-800 to-indigo-700 p-0.5 shadow-md shadow-violet-900/10 group-hover:scale-105 transition-transform shrink-0">
                <div className="w-full h-full bg-[#FAF8F5] rounded-[10px] flex items-center justify-center">
                  <Music className="w-4 h-4 sm:w-5 sm:h-5 text-violet-900" />
                </div>
              </div>
              <div className="truncate">
                <span className="text-xl sm:text-2xl font-serif font-bold tracking-tight text-slate-900 flex items-center gap-2">
                  VoxSight
                  <span className="px-1.5 sm:px-2 py-0.5 text-[9px] sm:text-[10px] font-sans font-semibold bg-violet-100 text-violet-900 border border-violet-200 rounded-full">
                    v{PROJECT_INFO.apkVersion}
                  </span>
                </span>
                <span className="hidden sm:block text-[9px] text-amber-800 font-sans tracking-widest uppercase font-bold truncate">
                  Choral Sight-Reading companion
                </span>
              </div>
            </a>
          </div>
        </div>
      </header>

      <main className="flex-grow w-full max-w-4xl mx-auto px-4 py-32 md:py-40">
        <h1 className="text-4xl md:text-5xl font-serif font-bold text-slate-900 mb-8">Privacy Policy</h1>
        
        <div className="space-y-6 text-slate-700 leading-relaxed">
          <p className="text-lg text-slate-600 mb-8">
            Last updated: {new Date().toLocaleDateString('en-US', { month: 'long', day: 'numeric', year: 'numeric' })}
          </p>

          <section className="space-y-4">
            <h2 className="text-2xl font-bold text-slate-900">1. Information We Collect</h2>
            <p>
              [Placeholder Text] We collect information you provide directly to us when you use VoxSight. 
              This may include your name, email address, and practice metrics used to generate your choral sight-reading progress.
            </p>
          </section>

          <section className="space-y-4">
            <h2 className="text-2xl font-bold text-slate-900">2. How We Use Your Information</h2>
            <p>
              [Placeholder Text] We use the information we collect to operate, maintain, and improve VoxSight. 
              This includes tracking your pitch accuracy, providing personalized feedback, and maintaining your practice streaks.
            </p>
          </section>

          <section className="space-y-4">
            <h2 className="text-2xl font-bold text-slate-900">3. Data Security</h2>
            <p>
              [Placeholder Text] We implement appropriate technical and organizational measures to protect your personal data 
              against unauthorized or unlawful processing, accidental loss, destruction, or damage.
            </p>
          </section>
          
          <section className="space-y-4">
            <h2 className="text-2xl font-bold text-slate-900">4. Third-Party Services</h2>
            <p>
              [Placeholder Text] We may use third-party services (such as Facebook and Google authentication) which have their 
              own privacy policies. We recommend reviewing their respective privacy documentation.
            </p>
          </section>

          <section className="space-y-4 pt-8">
            <h2 className="text-2xl font-bold text-slate-900">Contact Us</h2>
            <p>
              If you have any questions about this Privacy Policy, please contact us at: 
              <br/>
              <a href="mailto:privacy@voxsight.app" className="text-violet-700 hover:text-violet-600 font-semibold underline">privacy@voxsight.app</a>
            </p>
          </section>
        </div>
      </main>
    </div>
  );
};
