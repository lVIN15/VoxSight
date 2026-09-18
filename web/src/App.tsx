import { Navbar } from './components/Navbar';
import { Hero } from './components/Hero';
import { AboutSection } from './components/AboutSection';
import { ProblemSection } from './components/ProblemSection';
import { FeaturesSection } from './components/FeaturesSection';
import { HowItWorksSection } from './components/HowItWorksSection';
import { VideoDemoSection } from './components/VideoDemoSection';
import { ScreenshotsSection } from './components/ScreenshotsSection';
import { TechStackSection } from './components/TechStackSection';
import { TeamSection } from './components/TeamSection';
import { DownloadSection } from './components/DownloadSection';
import { Footer } from './components/Footer';
import { PrivacyPolicy } from './pages/PrivacyPolicy';
import { TermsOfService } from './pages/TermsOfService';

function App() {
  const path = window.location.pathname;

  if (path === '/privacy-policy') {
    return <PrivacyPolicy />;
  }

  if (path === '/terms-of-service') {
    return <TermsOfService />;
  }

  return (
    <div className="min-h-screen bg-[#FAF8F5] text-slate-900 selection:bg-amber-300 selection:text-slate-950 overflow-x-hidden">
      <Navbar />
      <main>
        <Hero />
        <AboutSection />
        <ProblemSection />
        <FeaturesSection />
        <HowItWorksSection />
        <VideoDemoSection />
        <ScreenshotsSection />
        <TechStackSection />
        <TeamSection />
        <DownloadSection />
      </main>
      <Footer />
    </div>
  );
}

export default App;
