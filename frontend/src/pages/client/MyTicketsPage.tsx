import React, { useState } from 'react';
import { ArrowLeft, Download, QrCode, CreditCard, History, Calendar, Clock, MapPin } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import { StudentHeader } from '../../layouts/StudentHeader';
import { MOCK_REGISTRATIONS } from '../../constants/mockData';
import type { Registration } from '../../types';
import { QRCodeSVG } from 'qrcode.react';

export const MyTicketsPage: React.FC = () => {
  const navigate = useNavigate();
  const [selectedTicket, setSelectedTicket] = useState<Registration | null>(null);

  const activeTickets = MOCK_REGISTRATIONS.filter(t => t.status === 'CONFIRMED');
  const pendingTickets = MOCK_REGISTRATIONS.filter(t => t.status === 'PENDING');
  const pastTickets = MOCK_REGISTRATIONS.filter(t => t.status === 'CHECKED_IN');

  // Format date correctly (e.g. May 18)
  const formatShortDate = (dateString: string) => {
    const d = new Date(dateString);
    return d.toLocaleDateString('en-US', { month: 'short', day: 'numeric' });
  };
  const formatTime = (dateString: string) => {
    return new Date(dateString).toLocaleTimeString('en-US', { hour: '2-digit', minute: '2-digit', hour12: false });
  };

  return (
    <div className="min-h-screen bg-[#FAFAFA] flex flex-col relative pb-20">
      <StudentHeader />

      <main className="flex flex-col w-full max-w-[1280px] mx-auto px-[16px] pt-[32px] gap-8">
        
        {/* Back Button */}
        <button 
          onClick={() => navigate('/')}
          className="flex items-center gap-2 text-[#757575] hover:text-[#1A1A1A] transition-colors w-fit"
        >
          <ArrowLeft size={20} />
          <span className="font-medium text-[16px]">Back to Catalog</span>
        </button>

        {/* Header Section */}
        <div className="flex flex-col gap-2">
          <h1 className="text-[30px] font-semibold text-[#1A1A1A] leading-[36px]">My Tickets</h1>
          <p className="text-[16px] text-[#757575]">Manage your workshop registrations and tickets</p>
        </div>

        {/* Summary Cards */}
        <div className="flex gap-4 w-full">
          {/* Active Tickets */}
          <div className="flex-1 bg-white border-[1.33px] border-[#E0E0E0] rounded-lg p-4 flex justify-between items-center">
            <div className="flex flex-col">
              <span className="text-[14px] text-[#757575]">Active Tickets</span>
              <span className="text-[24px] font-semibold text-[#1A1A1A]">{activeTickets.length}</span>
            </div>
            <div className="w-12 h-12 bg-[#E8F5E9] rounded-lg flex items-center justify-center">
              <QrCode size={24} className="text-[#1B5E20]" />
            </div>
          </div>

          {/* Attended */}
          <div className="flex-1 bg-white border-[1.33px] border-[#E0E0E0] rounded-lg p-4 flex justify-between items-center">
            <div className="flex flex-col">
              <span className="text-[14px] text-[#757575]">Attended</span>
              <span className="text-[24px] font-semibold text-[#1A1A1A]">{pastTickets.length}</span>
            </div>
            <div className="w-12 h-12 bg-[#EEEEEE] rounded-lg flex items-center justify-center">
              <History size={24} className="text-[#757575]" />
            </div>
          </div>

          {/* Pending Payment */}
          <div className="flex-1 bg-white border-[1.33px] border-[#E0E0E0] rounded-lg p-4 flex justify-between items-center">
            <div className="flex flex-col">
              <span className="text-[14px] text-[#757575]">Pending Payment</span>
              <span className="text-[24px] font-semibold text-[#1A1A1A]">{pendingTickets.length}</span>
            </div>
            <div className="w-12 h-12 bg-[#FFE0B2] rounded-lg flex items-center justify-center">
              <CreditCard size={24} className="text-[#F57C00]" />
            </div>
          </div>
        </div>

        {/* Active Tickets Section */}
        {activeTickets.length > 0 && (
          <div className="flex flex-col gap-4">
            <h2 className="text-[20px] font-semibold text-[#1A1A1A]">Active Tickets ({activeTickets.length})</h2>
            <div className="grid grid-cols-2 gap-6">
              {activeTickets.map(ticket => (
                <div key={ticket.id} className="bg-white border-[1.33px] border-[#E0E0E0] rounded-lg flex flex-col overflow-hidden relative">
                  {/* Top Color Line */}
                  <div className="h-[12px] w-full" style={{ background: 'linear-gradient(90deg, #1B5E20 0%, #43A047 100%)' }}></div>
                  
                  <div className="p-5 flex flex-col h-full relative">
                    <div className="flex justify-between items-start">
                      <div className="flex flex-col gap-2">
                        <h3 className="text-[18px] font-semibold text-[#1A1A1A]">{ticket.workshop.title}</h3>
                        <div className="bg-[#C8E6C9] rounded-md px-2 py-1 flex items-center w-fit">
                          <span className="text-[12px] font-medium text-[#2E7D32]">Confirmed</span>
                        </div>
                      </div>
                      <div className="bg-[#E8F5E9] rounded-md px-2 py-1">
                        <span className="text-[12px] font-medium text-[#1B5E20]">
                          {ticket.workshop.price_amount === 0 ? 'FREE' : `$${ticket.workshop.price_amount}`}
                        </span>
                      </div>
                    </div>

                    <div className="flex flex-col gap-2 mt-4">
                      <div className="flex items-center gap-2 text-[14px] text-[#1A1A1A]">
                        <Calendar size={16} className="text-[#757575]" />
                        <span>{formatShortDate(ticket.workshop.start_time)}</span>
                      </div>
                      <div className="flex items-center gap-2 text-[14px] text-[#1A1A1A]">
                        <Clock size={16} className="text-[#757575]" />
                        <span>{formatTime(ticket.workshop.start_time)} - {formatTime(ticket.workshop.end_time)}</span>
                      </div>
                      <div className="flex items-center gap-2 text-[14px] text-[#1A1A1A]">
                        <MapPin size={16} className="text-[#757575]" />
                        <span>{ticket.workshop.location}</span>
                      </div>
                    </div>

                    <div className="mt-auto pt-6 pb-2 flex gap-2">
                      <button 
                        onClick={() => setSelectedTicket(ticket)}
                        className="flex-1 bg-[#1B5E20] hover:bg-[#144d18] text-white rounded-lg h-[40px] flex items-center justify-center font-medium text-[16px] transition-colors"
                      >
                        <QrCode size={16} className="mr-2" />
                        View QR Code
                      </button>
                      <button className="w-[50px] h-[40px] border-[1.33px] border-[#E0E0E0] rounded-lg flex items-center justify-center hover:bg-gray-50 transition-colors">
                        <Download size={16} className="text-[#1A1A1A]" />
                      </button>
                    </div>

                    <div className="mt-4 pt-2 border-t border-dashed border-[#E0E0E0]">
                      <span className="text-[12px] text-[#757575]">Ticket ID: {ticket.id}</span>
                    </div>
                  </div>
                </div>
              ))}
            </div>
          </div>
        )}

        {/* Pending Payment Section */}
        {pendingTickets.length > 0 && (
          <div className="flex flex-col gap-4">
            <h2 className="text-[20px] font-semibold text-[#1A1A1A]">Pending Payment ({pendingTickets.length})</h2>
            <div className="grid grid-cols-2 gap-6">
              {pendingTickets.map(ticket => (
                <div key={ticket.id} className="bg-white border-[1.33px] border-[#FFB74D] rounded-lg flex flex-col overflow-hidden relative">
                  <div className="h-[12px] w-full bg-[#F57C00]"></div>
                  
                  <div className="p-5 flex flex-col h-full relative">
                    <div className="flex justify-between items-start">
                      <div className="flex flex-col gap-2">
                        <h3 className="text-[18px] font-semibold text-[#1A1A1A]">{ticket.workshop.title}</h3>
                        <div className="bg-[#FFE0B2] rounded-md px-2 py-1 flex items-center w-fit">
                          <span className="text-[12px] font-medium text-[#F57C00]">Pending Payment</span>
                        </div>
                      </div>
                      <div className="bg-[#FFE0B2] rounded-md px-2 py-1">
                        <span className="text-[12px] font-medium text-[#F57C00]">${ticket.workshop.price_amount}</span>
                      </div>
                    </div>

                    <div className="flex flex-col gap-2 mt-4">
                      <div className="flex items-center gap-2 text-[14px] text-[#1A1A1A]">
                        <Calendar size={16} className="text-[#757575]" />
                        <span>{formatShortDate(ticket.workshop.start_time)}</span>
                      </div>
                      <div className="flex items-center gap-2 text-[14px] text-[#1A1A1A]">
                        <Clock size={16} className="text-[#757575]" />
                        <span>{formatTime(ticket.workshop.start_time)} - {formatTime(ticket.workshop.end_time)}</span>
                      </div>
                    </div>

                    <div className="mt-auto pt-6">
                      <button className="w-full bg-[#F57C00] hover:bg-[#E65100] text-white rounded-lg h-[40px] flex items-center justify-center font-medium text-[16px] transition-colors">
                        Complete Payment
                      </button>
                    </div>
                  </div>
                </div>
              ))}
            </div>
          </div>
        )}

        {/* Past Workshops Section */}
        {pastTickets.length > 0 && (
          <div className="flex flex-col gap-4">
            <h2 className="text-[20px] font-semibold text-[#1A1A1A]">Past Workshops ({pastTickets.length})</h2>
            <div className="grid grid-cols-2 gap-6">
              {pastTickets.map(ticket => (
                <div key={ticket.id} className="bg-white/75 border-[1.33px] border-[#E0E0E0] rounded-lg flex flex-col overflow-hidden relative opacity-75">
                  <div className="h-[12px] w-full bg-[#EEEEEE]"></div>
                  
                  <div className="p-5 flex flex-col h-full relative">
                    <div className="flex flex-col gap-2">
                      <h3 className="text-[18px] font-semibold text-[#1A1A1A]">{ticket.workshop.title}</h3>
                      <div className="bg-[#EEEEEE] rounded-md px-2 py-1 flex items-center w-fit">
                        <span className="text-[12px] font-medium text-[#757575]">Used</span>
                      </div>
                    </div>

                    <div className="flex flex-col gap-2 mt-4">
                      <div className="flex items-center gap-2 text-[14px] text-[#757575]">
                        <Calendar size={16} className="text-[#757575]" />
                        <span>{formatShortDate(ticket.workshop.start_time)}</span>
                      </div>
                      <div className="flex items-center gap-2 text-[14px] text-[#1B5E20]">
                        <div className="w-4 h-4 flex items-center justify-center border-[1.33px] border-[#1B5E20] rounded-full">
                           <div className="w-1.5 h-1.5 bg-[#1B5E20] rounded-full"></div>
                        </div>
                        <span>Checked in at 13:55 PM</span>
                      </div>
                    </div>

                    <div className="mt-auto pt-6 pb-2 border-t border-dashed border-[#E0E0E0]">
                      <span className="text-[12px] text-[#757575]">Ticket ID: {ticket.id}</span>
                    </div>
                  </div>
                </div>
              ))}
            </div>
          </div>
        )}
      </main>

      {/* QR Code Modal Overlay */}
      {selectedTicket && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4">
          <div className="bg-white rounded-[16px] w-full max-w-[448px] shadow-2xl relative overflow-hidden flex flex-col">
            
            {/* Modal Header */}
            <div className="px-8 pt-8 pb-4 flex flex-col items-center text-center">
              <h2 className="text-[20px] font-semibold text-[#1A1A1A]">Your Ticket QR Code</h2>
              <p className="text-[14px] text-[#757575] mt-2">{selectedTicket.workshop.title}</p>
            </div>

            {/* QR Code Box */}
            <div className="px-8 flex justify-center">
              <div className="w-[320px] h-[320px] border-[1.33px] border-[#1B5E20] rounded-[12px] p-6 bg-white">
                <div className="w-full h-full bg-green-50/50 rounded-lg flex flex-col items-center justify-center">
                   <QRCodeSVG 
                      value={selectedTicket.qr_code_payload} 
                      size={200}
                      fgColor="#1B5E20"
                      level="Q"
                   />
                   <p className="text-[#757575] font-mono text-[12px] mt-4">{selectedTicket.qr_code_payload}</p>
                </div>
              </div>
            </div>

            {/* Details Box */}
            <div className="px-8 py-6">
               <div className="bg-[#E8F5E9] rounded-lg p-4 flex flex-col gap-4">
                  <div className="flex justify-between">
                     <div className="flex flex-col">
                        <span className="text-[14px] text-[#757575]">Date</span>
                        <span className="text-[14px] font-medium text-[#1A1A1A]">{formatShortDate(selectedTicket.workshop.start_time)}</span>
                     </div>
                     <div className="flex flex-col text-right">
                        <span className="text-[14px] text-[#757575]">Time</span>
                        <span className="text-[14px] font-medium text-[#1A1A1A]">{formatTime(selectedTicket.workshop.start_time)}</span>
                     </div>
                  </div>
                  <div className="flex flex-col">
                     <span className="text-[14px] text-[#757575]">Location</span>
                     <span className="text-[14px] font-medium text-[#1A1A1A]">{selectedTicket.workshop.location}</span>
                  </div>
               </div>
            </div>

            {/* Actions */}
            <div className="px-8 flex gap-3 pb-6">
              <button className="flex-1 bg-[#1B5E20] hover:bg-[#144d18] text-white rounded-lg h-[48px] flex items-center justify-center font-medium text-[16px] transition-colors">
                 <Download size={16} className="mr-2" />
                 Download QR
              </button>
              <button 
                onClick={() => setSelectedTicket(null)}
                className="flex-1 border-[1.33px] border-[#E0E0E0] hover:bg-gray-50 text-[#1A1A1A] rounded-lg h-[48px] flex items-center justify-center font-medium text-[16px] transition-colors"
              >
                 Close
              </button>
            </div>

            {/* Footer Text */}
            <div className="pb-8 text-center px-8">
               <p className="text-[12px] text-[#757575]">
                 Show this QR code at the workshop entrance for check-in
               </p>
            </div>
            
          </div>
        </div>
      )}

    </div>
  );
};
