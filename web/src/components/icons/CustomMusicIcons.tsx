import React from 'react';

export const TuningForkIcon: React.FC<{ className?: string }> = ({ className = "w-6 h-6" }) => (
  <svg className={className} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    {/* Acoustic Tuning Fork */}
    <path d="M7 3v7a5 5 0 0 0 10 0V3" />
    <path d="M12 15v6" />
    <circle cx="12" cy="21" r="1" fill="currentColor" />
    {/* Soundwave Rings */}
    <path d="M4 6a9 9 0 0 0 0 8" strokeDasharray="2 2" opacity="0.6" />
    <path d="M20 6a9 9 0 0 1 0 8" strokeDasharray="2 2" opacity="0.6" />
  </svg>
);

export const WaveformIcon: React.FC<{ className?: string }> = ({ className = "w-6 h-6" }) => (
  <svg className={className} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    {/* Rhythmic Vocal Waveform */}
    <path d="M2 12h2" />
    <path d="M6 12v-4" />
    <path d="M6 12v4" />
    <path d="M10 12v-7" />
    <path d="M10 12v7" />
    <path d="M14 12v-9" />
    <path d="M14 12v9" />
    <path d="M18 12v-5" />
    <path d="M18 12v5" />
    <path d="M20 12h2" />
  </svg>
);

export const TrebleClefIcon: React.FC<{ className?: string }> = ({ className = "w-6 h-6" }) => (
  <svg className={className} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" strokeLinejoin="round">
    {/* Stylized Musical Notation Treble Clef */}
    <path d="M12 21V3" />
    <path d="M12 3C10.5 3 9 4.5 9 6.5C9 9 12 11 12 13" />
    <path d="M12 13C12 16.5 8 16.5 8 13.5C8 10 16 10 16 14C16 18 12 21 9 19" />
    <circle cx="12" cy="21" r="1.5" fill="currentColor" />
  </svg>
);

export const StaffIsolationIcon: React.FC<{ className?: string }> = ({ className = "w-6 h-6" }) => (
  <svg className={className} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    {/* 4 Staves Lines with active highlighted staff line */}
    <line x1="3" y1="5" x2="21" y2="5" strokeOpacity="0.3" />
    <line x1="3" y1="9" x2="21" y2="9" strokeOpacity="0.3" />
    <line x1="3" y1="13" x2="21" y2="13" strokeWidth="3" />
    <line x1="3" y1="17" x2="21" y2="17" strokeOpacity="0.3" />
    <circle cx="12" cy="13" r="3" fill="currentColor" />
  </svg>
);
