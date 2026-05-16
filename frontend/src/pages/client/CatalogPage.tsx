import React, { useState } from 'react';
import { Search, SlidersHorizontal, Calendar, Users, Ticket } from 'lucide-react';
import { StudentHeader } from '../../layouts/StudentHeader';
import { WorkshopCard } from '../../components/common/WorkshopCard';
import { MOCK_WORKSHOPS, CATALOG_STATS } from '../../constants/mockData';

export const CatalogPage: React.FC = () => {
  const [searchQuery, setSearchQuery] = useState('');

  const filteredWorkshops = MOCK_WORKSHOPS.filter(
    (w) =>
      w.title.toLowerCase().includes(searchQuery.toLowerCase()) ||
      w.speaker.toLowerCase().includes(searchQuery.toLowerCase())
  );

  return (
    <div className="min-h-screen bg-[#FAFAFA] flex flex-col relative">
      {/* Fixed Header */}
      <div className="fixed top-0 left-0 right-0 z-50">
        <StudentHeader />
      </div>

      {/* Main Content Area */}
      <main className="flex-1 w-full flex flex-col items-center pt-[80px] px-[90px] pb-[40px]">
        <div className="w-full max-w-[1280px] relative mt-[24px]">
          
          {/* Hero Section */}
          <div className="flex flex-col gap-2 mb-[32px]">
            <h1 className="text-[30px] font-semibold text-[#1A1A1A] leading-[36px]">
              Skills & Career Week
            </h1>
            <p className="text-[16px] font-normal text-[#757575] leading-[24px] max-w-[480px]">
              Discover and register for professional development workshops
            </p>
          </div>

          {/* Search & Filter Bar */}
          <div className="flex items-center gap-[16px] mb-[24px]">
            <div className="relative flex-1 max-w-[1125.81px]">
              <Search className="absolute left-[16px] top-1/2 -translate-y-1/2 text-[#757575] w-5 h-5" />
              <input
                type="text"
                placeholder="Search by workshop title or speaker name..."
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                className="w-full h-[50.67px] bg-white border-[1.33px] border-[#E0E0E0] rounded-lg pl-[44px] pr-4 text-[16px] text-[#1A1A1A] placeholder:text-[rgba(26,26,26,0.5)] focus:outline-none focus:border-primary focus:ring-1 focus:ring-primary transition-colors"
              />
            </div>
            <button className="h-[50.67px] px-[16px] bg-white border-[1.33px] border-[#E0E0E0] rounded-lg flex items-center justify-center gap-2 hover:bg-gray-50 transition-colors">
              <SlidersHorizontal className="text-[#1A1A1A] w-5 h-5" />
              <span className="text-[16px] font-medium text-[#1A1A1A] leading-[24px]">Filters</span>
            </button>
          </div>

          {/* Stats Row */}
          <div className="grid grid-cols-1 md:grid-cols-3 gap-6 mb-[32px]">
            {/* Stat 1 */}
            <div className="bg-white border-[1.33px] border-[#E0E0E0] rounded-lg p-[17.33px] flex items-center justify-between h-[86.67px]">
              <div className="flex flex-col">
                <span className="text-[14px] font-normal text-[#757575] leading-[20px]">Total Workshops</span>
                <span className="text-[24px] font-semibold text-[#1A1A1A] leading-[32px]">{CATALOG_STATS.totalWorkshops}</span>
              </div>
              <div className="w-[48px] h-[48px] bg-[#E8F5E9] rounded-lg flex items-center justify-center">
                <Calendar className="w-6 h-6 text-primary" />
              </div>
            </div>
            
            {/* Stat 2 */}
            <div className="bg-white border-[1.33px] border-[#E0E0E0] rounded-lg p-[17.33px] flex items-center justify-between h-[86.67px]">
              <div className="flex flex-col">
                <span className="text-[14px] font-normal text-[#757575] leading-[20px]">Available Seats</span>
                <span className="text-[24px] font-semibold text-[#1A1A1A] leading-[32px]">{CATALOG_STATS.capacity_remaining.toLocaleString()}</span>
              </div>
              <div className="w-[48px] h-[48px] bg-[#E8F5E9] rounded-lg flex items-center justify-center">
                <Users className="w-6 h-6 text-primary" />
              </div>
            </div>

            {/* Stat 3 */}
            <div className="bg-white border-[1.33px] border-[#E0E0E0] rounded-lg p-[17.33px] flex items-center justify-between h-[86.67px]">
              <div className="flex flex-col">
                <span className="text-[14px] font-normal text-[#757575] leading-[20px]">Your Registrations</span>
                <span className="text-[24px] font-semibold text-[#1A1A1A] leading-[32px]">{CATALOG_STATS.yourRegistrations}</span>
              </div>
              <div className="w-[48px] h-[48px] bg-[#E8F5E9] rounded-lg flex items-center justify-center">
                <Ticket className="w-6 h-6 text-primary" />
              </div>
            </div>
          </div>

          {/* Results Label */}
          <div className="mb-[16px]">
            <span className="text-[14px] font-normal text-[#757575] leading-[20px]">
              Showing {filteredWorkshops.length} of {MOCK_WORKSHOPS.length} workshops
            </span>
          </div>

          {/* Workshop Grid */}
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-[24px] mb-[64px]">
            {filteredWorkshops.map((workshop) => (
              <WorkshopCard key={workshop.id} workshop={workshop} />
            ))}
          </div>

        </div>

        {/* Live Capacity Indicator */}
        <div className="fixed bottom-[40px] bg-[#E8F5E9] rounded-full px-4 py-2 flex items-center gap-3 shadow-md">
          <div className="w-2 h-2 bg-primary rounded-full opacity-80 animate-pulse"></div>
          <span className="text-[14px] font-normal text-[#757575] leading-[20px] text-center">
            Live capacity updates
          </span>
        </div>

      </main>
    </div>
  );
};
