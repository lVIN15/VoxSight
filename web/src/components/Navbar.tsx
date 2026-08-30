import React, { useState, useEffect } from 'react';
import { Download, Music, Menu, X } from 'lucide-react';
import { PROJECT_INFO } from '../data/mockData';

export const Navbar: React.FC = () => {
  const [scrolled, setScrolled] = useState(false);
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);

  useEffect(() => {
    const handleScroll = () => {
      setScrolled(window.scrollY > 20);
    };
    window.addEventListener('scroll', handleScroll);
    return () => window.removeEventListener('scroll', handleScroll);
  }, []);

  const navLinks = [
    { name: 'About', href: '#about' },
    { name: 'Problem', href: '#problem' },
    { name: 'Features', href: '#features' },
    { name: 'Workflow', href: '#how-it-works' },
    { name: 'Showcase', href: '#showcase' },
    { name: 'Tech Stack', href: '#tech-stack' },
    { name: 'Team', href: '#team' },
  ];

  return (
    <header
      className={`fixed top-0 left-0 right-0 z-50 transition-all duration-300 ${
        scrolled
          ? 'bg-[#FAF8F5]/92 backdrop-blur-md border-b border-[#F3EAE0] py-3 shadow-sm shadow-violet-900/5'
          : 'bg-transparent py-5'
      }`}
    >
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="flex items-center justify-between">
          
          {/* Logo */}
          <a href="#" className="flex items-center gap-3 group">
            <div className="w-10 h-10 rounded-xl bg-gradient-to-tr from-violet-900 via-violet-800 to-indigo-700 p-0.5 shadow-md shadow-violet-900/10 group-hover:scale-105 transition-transform">
              <div className="w-full h-full bg-[#FAF8F5] rounded-[10px] flex items-center justify-center">
                <Music className="w-5 h-5 text-violet-900" />
              </div>
            </div>
            <div>
              <span className="text-2xl font-serif font-bold tracking-tight text-slate-900 flex items-center gap-2">
                VoxSight
                <span className="px-2 py-0.5 text-[10px] font-sans font-semibold bg-violet-100 text-violet-900 border border-violet-200 rounded-full">
                  v{PROJECT_INFO.apkVersion}
                </span>
              </span>
              <span className="block text-[9px] text-amber-800 font-sans tracking-widest uppercase font-bold">
                Choral Sight-Reading companion
              </span>
            </div>
          </a>

          {/* Desktop Navigation Links */}
          <nav className="hidden lg:flex items-center gap-1 bg-[#F4EFEA]/80 p-1.5 rounded-full border border-[#E9DFD5]">
            {navLinks.map((link) => (
              <a
                key={link.name}
                href={link.href}
                className="px-4 py-1.5 text-xs font-sans font-semibold text-slate-700 hover:text-violet-900 hover:bg-white rounded-full transition-colors"
              >
                {link.name}
              </a>
            ))}
          </nav>

          {/* Download APK Action */}
          <div className="hidden sm:flex items-center gap-3">
            <a
              href="#download"
              className="inline-flex items-center gap-2 px-4 py-2 text-xs font-sans font-bold text-white bg-gradient-to-r from-violet-900 via-violet-800 to-indigo-800 hover:from-violet-950 hover:to-indigo-900 rounded-xl shadow-md shadow-violet-900/15 hover:-translate-y-0.5 transition-all duration-200"
            >
              <Download className="w-4 h-4 text-amber-200" />
              <span>Download APK</span>
            </a>
          </div>

          {/* Mobile Hamburger Button */}
          <button
            onClick={() => setMobileMenuOpen(!mobileMenuOpen)}
            className="lg:hidden p-2 text-slate-700 hover:text-violet-900 rounded-lg hover:bg-violet-100/50"
            aria-label="Toggle menu"
          >
            {mobileMenuOpen ? <X className="w-6 h-6" /> : <Menu className="w-6 h-6" />}
          </button>
        </div>
      </div>

      {/* Mobile Drawer Menu */}
      {mobileMenuOpen && (
        <div className="lg:hidden bg-[#FAF8F5]/98 backdrop-blur-xl border-b border-[#F3EAE0] px-4 pt-3 pb-6 shadow-xl animate-in slide-in-from-top duration-200">
          <div className="flex flex-col gap-1.5">
            {navLinks.map((link) => (
              <a
                key={link.name}
                href={link.href}
                onClick={() => setMobileMenuOpen(false)}
                className="px-4 py-2.5 text-sm font-sans font-semibold text-slate-800 hover:text-violet-900 hover:bg-violet-100/50 rounded-lg transition-colors"
              >
                {link.name}
              </a>
            ))}
            <div className="pt-3 border-t border-[#F3EAE0] mt-2">
              <a
                href="#download"
                onClick={() => setMobileMenuOpen(false)}
                className="flex items-center justify-center gap-2 w-full py-3 text-sm font-sans font-bold text-white bg-gradient-to-r from-violet-900 to-indigo-800 rounded-xl shadow-md"
              >
                <Download className="w-4 h-4 text-amber-200" />
                Download Android APK ({PROJECT_INFO.apkSize})
              </a>
            </div>
          </div>
        </div>
      )}
    </header>
  );
};
