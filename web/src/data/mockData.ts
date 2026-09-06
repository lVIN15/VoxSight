import type { FeatureItem, TeamMember, TechItem, ScreenshotItem } from '../types';

export const PROJECT_INFO = {
  title: 'VoxSight',
  subtitle: 'A Mobile Application for Independent Choral Sight-Reading Practice',
  tagline: 'Bridging the literacy gap in amateur choral ensembles with AI-driven sheet music scanning, voice isolation, and real-time pitch feedback.',
  teamCode: '2526-sem2-it332-51',
  apkVersion: '1.0.0',
  apkSize: '29.7 MB',
  minAndroidVersion: 'Android 8.0 (API 26)+',
  downloadUrl: 'https://github.com/lVIN15/VoxSight/releases/download/v1.0.0/voxsight-v1.0.0.apk',
  githubRepo: 'https://github.com/lVIN15/VoxSight',
};

export const CORE_FEATURES: FeatureItem[] = [
  {
    id: 'omr',
    title: 'Controlled OMR Digitization',
    shortDesc: 'Instantly convert printed SATB sheet music into playable MusicXML digital scores.',
    fullDesc: 'Scans standardized, high-contrast printed choir scores and parses note semantics, clefs, time signatures, and 4-part staff layouts into playable MusicXML within seconds.',
    metric: '≤10s / page',
    iconName: 'ScanLine',
    badge: 'OMR Engine',
    highlights: [
      'Approx. 80–85% SATB structural separation accuracy under controlled input',
      'Supports high-contrast digital sheet music scans (JPEG, PNG, PDF)',
      'Parses timing, measure breaks, and pitch notation into standard MusicXML',
      'Eliminates manual score re-entry for weekly choir repertoire'
    ]
  },
  {
    id: 'selective-focus',
    title: 'Audio-Visual Selective Focus',
    shortDesc: 'Isolate your voice section while dimming unassigned staves to reduce visual & auditory cognitive overload.',
    fullDesc: 'Maintains your selected vocal part (Soprano, Alto, Tenor, Bass) at 100% visual opacity while dynamically dimming unassigned parts to ≤20% opacity. Plays isolated synthesized human vowel tones while suppressing background tracks.',
    metric: '≤20% Staff Opacity',
    iconName: 'Eye',
    badge: 'Cognitive Aid',
    highlights: [
      'Visual stave opacity reduction to ≤20% for non-selected voice parts',
      'Substantial background track volume suppression to prevent harmonic confusion',
      'Synthesized human vocal tones (Aah/Ooh vowel soundfonts) instead of piano MIDI',
      'Instant toggle between Soprano, Alto, Tenor, and Bass lines'
    ]
  },
  {
    id: 'dynamic-tracking',
    title: 'Dynamic Score Tracking',
    shortDesc: 'Follow a synchronized real-time visual playhead mapped directly to audio playback.',
    fullDesc: 'Maps precise note timing metadata from MusicXML to an automated visual playhead indicator that dynamically highlights active notes as audio plays, reinforcing visuo-motor sight-reading skills.',
    metric: '≤0.1s Highlight Latency',
    iconName: 'Activity',
    badge: 'Visuo-Motor',
    highlights: [
      'Ultra-low visual-to-audio synchronization latency threshold of ≤0.1 seconds',
      'Dynamically highlights active noteheads in real time during playback',
      'Establishes immediate mental mapping between printed symbol and pitch sound',
      'Supports variable practice tempos for gradual learning'
    ]
  },
  {
    id: 'pitch-feedback',
    title: 'Real-Time Pitch Feedback',
    shortDesc: 'Sing into your device microphone and receive immediate visual intonation guidance.',
    fullDesc: 'Captures sung vocal frequencies through the device microphone, processes frequency data against digitized target notes, and displays immediate color-coded intonation feedback to enable independent self-correction.',
    metric: '≥85% Pitch Match Accuracy',
    iconName: 'Mic',
    badge: 'Interactive Mic',
    highlights: [
      'Low-latency visual pitch feedback display within ≤0.5 seconds of vocal input',
      'Target pitch accuracy detection rate of ≥85% under quiet conditions',
      'Clear color-coded guidance (Green = In-Tune, Amber = Near, Red = Out-of-Tune)',
      'Identifies specific measures requiring targeted home repetition'
    ]
  }
];

export const TECH_STACK: TechItem[] = [
  {
    name: 'Android Native (Kotlin)',
    category: 'Mobile',
    description: 'Built natively with Kotlin and Jetpack Compose for high-performance audio synthesis and responsive UI layout.',
    badge: 'Mobile App',
    icon: 'Smartphone'
  },
  {
    name: 'Kotlin Coroutines & Flow',
    category: 'Mobile',
    description: 'Asynchronous state management for low-latency microphone processing and real-time playhead updates.',
    badge: 'Async Core',
    icon: 'Cpu'
  },
  {
    name: 'Synthesized Vocal Soundfonts',
    category: 'Audio & Synthesis',
    description: 'Generates sustained human vocal vowel tones ("Aah"/"Ooh") rather than percussive piano tones for accurate vocal pitch tuning.',
    badge: 'Vocal Audio',
    icon: 'Music'
  },
  {
    name: 'YIN Pitch Detection DSP',
    category: 'Audio & Synthesis',
    description: 'Digital signal processing algorithm measuring sung vocal frequencies from device mic input with high precision.',
    badge: 'DSP Engine',
    icon: 'Radio'
  },
  {
    name: 'Controlled OMR Parser',
    category: 'Music & OMR',
    description: 'Optical Music Recognition pipeline extracting SATB stave tracks into playable MusicXML syntax.',
    badge: 'Notation AI',
    icon: 'FileText'
  },
  {
    name: 'MusicXML Standard Format',
    category: 'Music & OMR',
    description: 'Standardized digital sheet music representation preserving timing, pitch, measure breaks, and part separation.',
    badge: 'Score Standard',
    icon: 'Code'
  }
];

export const TEAM_MEMBERS: TeamMember[] = [
  {
    name: 'Teodoro D. Castillo Jr.',
    role: 'Lead Systems Architect & Core OMR Engineer',
    degree: 'B.S. Information Technology',
    githubUrl: '#',
    linkedinUrl: '#'
  },
  {
    name: 'Elvin O. Lagamo Jr.',
    role: 'Lead Developer & Sheet Music Output Testing Specialist',
    degree: 'B.S. Information Technology',
    githubUrl: '#',
    linkedinUrl: '#'
  },
  {
    name: 'Denzel Don L. Sala',
    role: 'Mobile UI/UX & Android Developer',
    degree: 'B.S. Information Technology',
    githubUrl: '#',
    linkedinUrl: '#'
  },
  {
    name: 'John Emmanuel S. Sevilla',
    role: 'Full-Stack & Integration Engineer',
    degree: 'B.S. Information Technology',
    githubUrl: '#',
    linkedinUrl: '#'
  },
  {
    name: 'German Oliver E. Velasco',
    role: 'Quality Assurance & Testing Lead',
    degree: 'B.S. Information Technology',
    githubUrl: '#',
    linkedinUrl: '#'
  }
];

export const APP_SCREENSHOTS: ScreenshotItem[] = [
  {
    id: 'scan',
    title: 'Controlled OMR Score Scanner',
    caption: 'Capture or import printed choir sheet music for rapid digitizing into MusicXML.',
    category: 'Scan & OMR',
    imagePlaceholder: 'omr-scanner'
  },
  {
    id: 'focus',
    title: 'Audio-Visual Selective Focus',
    caption: 'Select Tenor line: Alto, Soprano, and Bass staves instantly dim to ≤20% opacity.',
    category: 'Selective Focus',
    imagePlaceholder: 'selective-focus'
  },
  {
    id: 'pitch',
    title: 'Real-Time Intonation Feedback',
    caption: 'Sing into the mic while watching live color-coded pitch match feedback on the staff.',
    category: 'Live Pitch',
    imagePlaceholder: 'live-pitch'
  },
  {
    id: 'analytics',
    title: 'Practice & Intonation Summary',
    caption: 'Review pitch accuracy percentages across measures to target problem passages.',
    category: 'Analytics',
    imagePlaceholder: 'analytics-summary'
  }
];
