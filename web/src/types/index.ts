export type VoicePart = 'Soprano' | 'Alto' | 'Tenor' | 'Bass';

export interface FeatureItem {
  id: string;
  title: string;
  shortDesc: string;
  fullDesc: string;
  metric: string;
  iconName: string;
  badge: string;
  highlights: string[];
}

export interface TeamMember {
  name: string;
  role: string;
  degree: string;
  avatarUrl?: string;
  githubUrl?: string;
  linkedinUrl?: string;
  email?: string;
}

export interface TechItem {
  name: string;
  category: 'Mobile' | 'Audio & Synthesis' | 'Music & OMR' | 'Core Architecture';
  description: string;
  badge: string;
  icon: string;
}

export interface ScreenshotItem {
  id: string;
  title: string;
  caption: string;
  category: 'Scan & OMR' | 'Selective Focus' | 'Live Pitch' | 'Analytics';
  imagePlaceholder: string;
}

export interface PitchFeedbackState {
  sungPitch: string;
  targetPitch: string;
  centsOff: number;
  status: 'in-tune' | 'sharp' | 'flat';
  accuracy: number;
}
