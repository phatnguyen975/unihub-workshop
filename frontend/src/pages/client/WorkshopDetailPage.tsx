import React, { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { 
  ArrowLeft, Calendar, Clock, MapPin, Users, Zap, Sparkles, Download, 
  Loader2, Check
} from 'lucide-react';
import { QRCodeSVG } from 'qrcode.react';
import { StudentHeader } from '../../layouts/StudentHeader';
import { MOCK_WORKSHOPS } from '../../constants/mockData';

type PageState = 'INITIAL' | 'RESERVED' | 'PROCESSING' | 'SUCCESS';

export const WorkshopDetailPage: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  
  const workshop = MOCK_WORKSHOPS.find(w => w.id === id);
  
  const [pageState, setPageState] = useState<PageState>('INITIAL');
  const [timeLeft, setTimeLeft] = useState(300); // 5 minutes

  useEffect(() => {
    let timer: number;
    if (pageState === 'RESERVED' && timeLeft > 0) {
      timer = setInterval(() => {
        setTimeLeft(prev => prev - 1);
      }, 1000);
    } else if (timeLeft === 0 && pageState === 'RESERVED') {
      alert("Reservation expired, please try again");
      setPageState('INITIAL');
      setTimeLeft(300);
    }
    return () => clearInterval(timer);
  }, [pageState, timeLeft]);

  if (!workshop) {
    return (
      <div className="min-h-screen bg-[#FAFAFA] flex flex-col items-center justify-center">
        <StudentHeader />
        <div className="mt-20">Workshop not found</div>
        <button onClick={() => navigate('/')} className="mt-4 text-primary hover:underline">Return to Catalog</button>
      </div>
    );
  }

  const isSoldOut = workshop.capacity_remaining === 0;
  const isAlmostFull = workshop.capacity_remaining > 0 && workshop.capacity_remaining <= 5;
  const workshopFrontendStatus = isSoldOut ? 'SOLD_OUT' : (isAlmostFull ? 'ALMOST_FULL' : 'AVAILABLE');

  const handleRegisterClick = () => {
    if (workshopFrontendStatus !== 'SOLD_OUT') {
      if (workshop.price_amount === 0) {
        setPageState('PROCESSING');
        setTimeout(() => {
          setPageState('SUCCESS');
        }, 1500);
      } else {
        setPageState('RESERVED');
        setTimeLeft(300);
      }
    }
  };

  const handleCompletePaymentClick = () => {
    setPageState('PROCESSING');
    setTimeout(() => {
      setPageState('SUCCESS');
    }, 3000);
  };

  const formatTime = (seconds: number) => {
    const m = Math.floor(seconds / 60);
    const s = seconds % 60;
    return `${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}`;
  };

  const statusConfig = {
    AVAILABLE: { label: 'Available', bg: 'bg-[#C8E6C9]', text: 'text-[#2E7D32]' },
    ALMOST_FULL: { label: workshop.urgency_label || 'Almost Full', bg: 'bg-[#FFE0B2]', text: 'text-[#F57C00]' },
    SOLD_OUT: { label: 'Sold Out', bg: 'bg-[#FFCDD2]', text: 'text-[#C62828]' },
  } as const;

  const badge = statusConfig[workshopFrontendStatus];

  // Formatting date and time
  const startDate = new Date(workshop.start_time);
  const endDate = new Date(workshop.end_time);
  const formattedDate = startDate.toLocaleDateString('en-US', { month: 'short', day: 'numeric' });
  const formattedTimeRange = `${startDate.toLocaleTimeString('en-US', { hour: '2-digit', minute: '2-digit', hour12: false })} - ${endDate.toLocaleTimeString('en-US', { hour: '2-digit', minute: '2-digit', hour12: false })}`;

  return (
    <div className="min-h-screen bg-[#FAFAFA] flex flex-col">
      <div className="fixed top-0 left-0 right-0 z-40">
        <StudentHeader />
      </div>

      <main className="flex-1 w-full flex flex-col items-center pt-[80px] px-[90px] pb-[80px]">
        
        {/* Reserved Banner */}
        {pageState === 'RESERVED' && (
          <div className="w-full max-w-[1280px] bg-[#757575] border-l-4 border-[#424242] rounded-lg p-4 mt-6 flex justify-between items-center text-white">
            <div className="flex items-center gap-4">
              <div className="w-10 h-10 bg-[#424242] rounded-full flex items-center justify-center">
                <Clock className="w-5 h-5 text-white" />
              </div>
              <div>
                <div className="font-semibold text-[16px]">Your seat has been reserved</div>
                <div className="text-[14px] text-white/80">Complete payment within {formatTime(timeLeft)} to confirm registration</div>
              </div>
            </div>
            <div className="text-[24px] font-bold">{formatTime(timeLeft)}</div>
          </div>
        )}

        <div className="w-full max-w-[1280px] relative mt-[24px]">
          
          {/* Back Button */}
          <button 
            onClick={() => navigate('/')}
            className="flex items-center gap-2 text-[16px] font-medium text-[#757575] hover:text-[#1A1A1A] transition-colors mb-6"
          >
            <ArrowLeft className="w-5 h-5" />
            Back to Catalog
          </button>

          <div className="flex flex-col lg:flex-row gap-6">
            
            {/* Left Column */}
            <div className="w-full lg:w-[824px] flex flex-col gap-6">
              
              {/* Main Card */}
              <div className="bg-white border-[1.33px] border-[#E0E0E0] rounded-lg overflow-hidden">
                <div className="relative h-[256px] w-full flex flex-col items-center justify-center p-8 text-center" style={{ background: 'linear-gradient(135deg, #1B5E20 0%, #43A047 100%)' }}>
                  <div className="absolute inset-0 bg-black/10"></div>
                  
                  <div className={`absolute top-4 right-4 ${badge.bg} rounded-md px-3 py-1 flex items-center z-10`}>
                    <span className={`text-[14px] font-medium ${badge.text}`}>
                      {badge.label}
                    </span>
                  </div>

                  <h1 className="relative z-10 text-[30px] font-semibold text-white leading-[36px] mb-2 max-w-[80%]">
                    {workshop.title}
                  </h1>
                  <p className="relative z-10 text-[18px] font-normal text-white/90 leading-[28px]">
                    {workshop.subtitle}
                  </p>
                </div>

                <div className="p-6">
                  <div className="grid grid-cols-1 sm:grid-cols-2 gap-6">
                    <div className="flex items-center gap-3">
                      <div className="w-10 h-10 bg-[#E8F5E9] rounded-lg flex items-center justify-center shrink-0">
                        <Calendar className="w-5 h-5 text-primary" />
                      </div>
                      <div>
                        <div className="text-[14px] text-[#757575] leading-[20px]">Date</div>
                        <div className="text-[16px] font-medium text-[#1A1A1A] leading-[24px]">{formattedDate}, 2026</div>
                      </div>
                    </div>
                    
                    <div className="flex items-center gap-3">
                      <div className="w-10 h-10 bg-[#E8F5E9] rounded-lg flex items-center justify-center shrink-0">
                        <Clock className="w-5 h-5 text-primary" />
                      </div>
                      <div>
                        <div className="text-[14px] text-[#757575] leading-[20px]">Time</div>
                        <div className="text-[16px] font-medium text-[#1A1A1A] leading-[24px]">{formattedTimeRange}</div>
                      </div>
                    </div>

                    <div className="flex items-center gap-3">
                      <div className="w-10 h-10 bg-[#E8F5E9] rounded-lg flex items-center justify-center shrink-0">
                        <MapPin className="w-5 h-5 text-primary" />
                      </div>
                      <div>
                        <div className="text-[14px] text-[#757575] leading-[20px]">Location</div>
                        <div className="text-[16px] font-medium text-[#1A1A1A] leading-[24px]">{workshop.location}</div>
                      </div>
                    </div>

                    <div className="flex items-center gap-3">
                      <div className="w-10 h-10 bg-[#E8F5E9] rounded-lg flex items-center justify-center shrink-0">
                        <Users className="w-5 h-5 text-primary" />
                      </div>
                      <div>
                        <div className="text-[14px] text-[#757575] leading-[20px]">Capacity</div>
                        <div className="text-[16px] font-medium text-[#1A1A1A] leading-[24px]">{workshop.capacity_remaining} of {workshop.capacity_total} seats available</div>
                      </div>
                    </div>
                  </div>

                  {workshop.viewer_count > 100 && (
                    <div className="mt-6 bg-[#FFF3E0] border-[1.33px] border-[#FFE0B2] rounded-lg p-3 flex items-center gap-2">
                      <Zap className="w-5 h-5 text-[#F57C00] shrink-0" />
                      <span className="text-[14px] font-semibold text-[#F57C00]">High demand: {workshop.viewer_count} people are viewing this workshop</span>
                    </div>
                  )}
                </div>
              </div>

              {/* AI Summary Card */}
              <div className="bg-white border-[1.33px] border-[#E0E0E0] rounded-lg p-[25.33px] flex flex-col gap-4">
                <div className="flex items-center gap-2">
                  <Sparkles className="w-5 h-5 text-primary" />
                  <h2 className="text-[20px] font-semibold text-[#1A1A1A] leading-[30px]">AI-Generated Summary</h2>
                </div>
                <div className="flex flex-col gap-3">
                  {workshop.ai_summary.split('\n\n').map((para, i) => (
                    <p key={i} className="text-[16px] font-normal text-[#1A1A1A] leading-[26px]">
                      {para}
                    </p>
                  ))}
                </div>
              </div>

              {/* About Speaker Card */}
              <div className="bg-white border-[1.33px] border-[#E0E0E0] rounded-lg p-[25.33px] flex flex-col gap-4">
                <h2 className="text-[20px] font-semibold text-[#1A1A1A] leading-[30px]">About the Speaker</h2>
                <div className="flex gap-4">
                  <div className="w-16 h-16 rounded-full flex items-center justify-center text-white font-semibold text-[20px] shrink-0" style={{ background: 'linear-gradient(135deg, #1B5E20 0%, #43A047 100%)' }}>
                    {workshop.speaker_initials}
                  </div>
                  <div className="flex flex-col">
                    <h3 className="text-[18px] font-semibold text-[#1A1A1A] leading-[27px]">{workshop.speaker}</h3>
                    <div className="text-[14px] font-normal text-primary leading-[20px] mb-2">{workshop.speaker_title}</div>
                    <p className="text-[14px] font-normal text-[#757575] leading-[23px]">{workshop.speaker_bio}</p>
                  </div>
                </div>
              </div>

              {/* What You'll Learn Card */}
              <div className="bg-white border-[1.33px] border-[#E0E0E0] rounded-lg p-[25.33px] flex flex-col gap-4">
                <h2 className="text-[20px] font-semibold text-[#1A1A1A] leading-[30px]">What You'll Learn</h2>
                <ul className="flex flex-col gap-3">
                  {workshop.learning_outcomes.map((item, i) => (
                    <li key={i} className="flex items-start gap-3">
                      <div className="w-5 h-5 bg-[#E8F5E9] rounded-full flex items-center justify-center shrink-0 mt-[2px]">
                        <div className="w-2 h-2 bg-primary rounded-full"></div>
                      </div>
                      <span className="text-[14px] font-normal text-[#1A1A1A] leading-[20px]">{item}</span>
                    </li>
                  ))}
                </ul>
              </div>

              {/* Brochure Bar */}
              <div className="bg-[#E8F5E9] border-[1.33px] border-primary/20 rounded-lg p-4 flex justify-between items-center">
                <div className="flex items-center gap-3">
                  <Download className="w-5 h-5 text-primary" />
                  <div className="flex flex-col">
                    <span className="text-[16px] font-medium text-[#1A1A1A] leading-[24px]">Workshop Brochure</span>
                    <span className="text-[14px] font-normal text-[#757575] leading-[20px]">Detailed agenda and materials (PDF, {workshop.brochure_size})</span>
                  </div>
                </div>
                <button className="h-10 px-6 bg-primary text-white rounded-lg text-[16px] font-medium hover:bg-[#154d19] transition-colors shrink-0">
                  Download
                </button>
              </div>

            </div>

            {/* Right Column */}
            <div className="w-full lg:w-[400px]">
              <div className="bg-white border-[1.33px] border-[#E0E0E0] rounded-lg p-[25.33px] flex flex-col sticky top-[104px]">
                
                <div className="mb-4">
                  <div className="text-[30px] font-bold text-[#1A1A1A] leading-[36px]">
                    {workshop.price_amount === 0 ? 'FREE' : `$${workshop.price_amount}`}
                  </div>
                  <div className="text-[14px] font-normal text-[#757575] leading-[20px]">
                    Per participant
                  </div>
                </div>

                {workshopFrontendStatus === 'ALMOST_FULL' && pageState === 'INITIAL' && (
                  <div className="bg-[#FFF3E0] border-[1.33px] border-[#FFE0B2] rounded-lg p-3 flex flex-col gap-1 mb-6">
                    <div className="text-[14px] font-semibold text-[#F57C00] leading-[20px]">
                      ⚡ {workshop.urgency_label}
                    </div>
                    <div className="text-[12px] font-normal text-[#EF6C00] leading-[16px]">
                      Seats filling fast - register now to secure your spot
                    </div>
                  </div>
                )}

                {pageState === 'INITIAL' && (
                  <button 
                    onClick={handleRegisterClick}
                    disabled={workshopFrontendStatus === 'SOLD_OUT'}
                    className={`w-full h-[48px] rounded-lg text-[16px] font-medium flex items-center justify-center transition-colors mb-6
                      ${workshopFrontendStatus === 'SOLD_OUT' 
                        ? 'bg-[#757575] text-white cursor-not-allowed' 
                        : 'bg-primary text-white hover:bg-[#154d19]'}`}
                  >
                    {workshopFrontendStatus === 'SOLD_OUT' ? 'Sold Out' : 'Register Now'}
                  </button>
                )}

                {pageState === 'RESERVED' && (
                  <button 
                    onClick={handleCompletePaymentClick}
                    className="w-full h-[48px] bg-primary text-white hover:bg-[#154d19] rounded-lg text-[16px] font-medium flex items-center justify-center transition-colors mb-6"
                  >
                    Complete Payment
                  </button>
                )}

                <div className="flex flex-col gap-3 mt-2">
                  <div className="flex items-center gap-2">
                    <div className="w-5 h-5 bg-[#E8F5E9] rounded flex items-center justify-center shrink-0">
                      <div className="w-2 h-2 bg-primary rounded-full"></div>
                    </div>
                    <span className="text-[14px] font-normal text-[#757575]">Instant confirmation</span>
                  </div>
                  <div className="flex items-center gap-2">
                    <div className="w-5 h-5 bg-[#E8F5E9] rounded flex items-center justify-center shrink-0">
                      <div className="w-2 h-2 bg-primary rounded-full"></div>
                    </div>
                    <span className="text-[14px] font-normal text-[#757575]">QR code ticket via email</span>
                  </div>
                  <div className="flex items-center gap-2">
                    <div className="w-5 h-5 bg-[#E8F5E9] rounded flex items-center justify-center shrink-0">
                      <div className="w-2 h-2 bg-primary rounded-full"></div>
                    </div>
                    <span className="text-[14px] font-normal text-[#757575]">Certificate of completion</span>
                  </div>
                  <div className="flex items-center gap-2">
                    <div className="w-5 h-5 bg-[#E8F5E9] rounded flex items-center justify-center shrink-0">
                      <div className="w-2 h-2 bg-primary rounded-full"></div>
                    </div>
                    <span className="text-[14px] font-normal text-[#757575]">Free cancellation until 24h before</span>
                  </div>
                </div>

              </div>
            </div>

          </div>
        </div>
      </main>

      {/* Processing Modal Overlay */}
      {pageState === 'PROCESSING' && (
        <div className="fixed inset-0 bg-black/60 z-50 flex items-center justify-center px-4">
          <div className="bg-white rounded-[16px] w-[448px] p-8 flex flex-col items-center justify-center shadow-xl">
            <Loader2 className="w-16 h-16 text-primary animate-spin mb-6" />
            <h2 className="text-[20px] font-semibold text-[#1A1A1A] mb-2 text-center">
              {workshop.price_amount === 0 ? "Processing Registration" : "Redirecting to payment page"}
            </h2>
            <p className="text-[16px] text-[#757575] text-center">Please do not close this tab</p>
          </div>
        </div>
      )}

      {/* Success Modal Overlay */}
      {pageState === 'SUCCESS' && (
        <div className="fixed inset-0 bg-black/60 z-50 flex items-center justify-center px-4 py-8 overflow-y-auto">
          <div className="bg-white rounded-[16px] w-[448px] p-8 flex flex-col items-center shadow-xl my-auto">
            <div className="w-16 h-16 bg-[#C8E6C9] rounded-full flex items-center justify-center mb-6 shrink-0">
              <Check className="w-8 h-8 text-white" strokeWidth={3} />
            </div>
            
            <h2 className="text-[24px] font-semibold text-[#1A1A1A] mb-2 text-center">Registration Successful!</h2>
            <p className="text-[16px] text-[#757575] text-center mb-6">Your ticket has been confirmed and sent to your email</p>
            
            <div className="w-full bg-[#E8F5E9] rounded-lg p-4 mb-6">
              <h3 className="text-[18px] font-semibold text-[#1A1A1A] mb-3">{workshop.title}</h3>
              <div className="flex flex-col gap-2">
                <div className="flex items-center gap-2">
                  <Calendar className="w-4 h-4 text-[#757575]" />
                  <span className="text-[14px] text-[#1A1A1A]">{formattedDate}, 2026</span>
                </div>
                <div className="flex items-center gap-2">
                  <Clock className="w-4 h-4 text-[#757575]" />
                  <span className="text-[14px] text-[#1A1A1A]">{formattedTimeRange}</span>
                </div>
                <div className="flex items-center gap-2">
                  <MapPin className="w-4 h-4 text-[#757575]" />
                  <span className="text-[14px] text-[#1A1A1A]">{workshop.location}</span>
                </div>
              </div>
            </div>

            <div className="w-full bg-white border border-[#E0E0E0] rounded-lg p-4 mb-6 flex items-center justify-center">
              <QRCodeSVG value={`ticket-${workshop.id}-${Date.now()}`} size={128} />
            </div>

            <button 
              onClick={() => navigate('/')}
              className="w-full h-[48px] bg-primary text-white hover:bg-[#154d19] rounded-lg text-[16px] font-medium flex items-center justify-center transition-colors"
            >
              View My Tickets
            </button>
          </div>
        </div>
      )}

    </div>
  );
};
