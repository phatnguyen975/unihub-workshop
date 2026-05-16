import React from 'react';
import { useNavigate } from 'react-router-dom';
import { Calendar, Clock, Users, Tag, Zap } from 'lucide-react';
import type { Workshop } from '../../types';

interface WorkshopCardProps {
  workshop: Workshop;
}

export const WorkshopCard: React.FC<WorkshopCardProps> = ({ workshop }) => {
  const navigate = useNavigate();
  const { title, speaker, price_amount, start_time, end_time, capacity_remaining, capacity_total, urgency_label } = workshop;

  const isSoldOut = capacity_remaining === 0;
  const isAlmostFull = capacity_remaining > 0 && capacity_remaining <= 5;
  const workshopFrontendStatus = isSoldOut ? 'SOLD_OUT' : (isAlmostFull ? 'ALMOST_FULL' : 'AVAILABLE');

  const statusConfig = {
    AVAILABLE: { label: 'Available', bg: 'bg-[#C8E6C9]', text: 'text-[#2E7D32]' },
    ALMOST_FULL: { label: urgency_label || 'Almost Full', bg: 'bg-[#FFE0B2]', text: 'text-[#F57C00]' },
    SOLD_OUT: { label: 'Sold Out', bg: 'bg-[#FFCDD2]', text: 'text-[#C62828]' },
  } as const;

  const badge = statusConfig[workshopFrontendStatus];

  const startDate = new Date(start_time);
  const formattedDate = startDate.toLocaleDateString('en-US', { weekday: 'short', month: 'short', day: 'numeric' });
  const formattedStartTime = startDate.toLocaleTimeString('en-US', { hour: '2-digit', minute: '2-digit', hour12: false });
  const formattedEndTime = new Date(end_time).toLocaleTimeString('en-US', { hour: '2-digit', minute: '2-digit', hour12: false });

  return (
    <div
      className="bg-white border-[1.33px] border-[#E0E0E0] rounded-lg overflow-hidden cursor-pointer hover:shadow-md transition-shadow flex flex-col h-full"
      onClick={() => navigate(`/workshops/${workshop.id}`)}
      role="button"
      tabIndex={0}
      aria-label={`View details for ${title}`}
      onKeyDown={(e) => e.key === 'Enter' && navigate(`/workshops/${workshop.id}`)}
    >
      {/* Top Image Banner */}
      <div 
        className="relative h-[160px] w-full"
        style={{ background: 'linear-gradient(135deg, #1B5E20 0%, #43A047 100%)' }}
      >
        <div className="absolute inset-0 bg-black/10"></div>
        
        <div className="absolute inset-0 p-3 flex justify-between items-start">
          {/* Price Badge */}
          <div className="bg-white/90 rounded-md px-2 py-1 flex items-center">
            <span className="text-[12px] font-medium text-primary leading-[16px]">
              {price_amount === 0 ? 'FREE' : `$${price_amount}`}
            </span>
          </div>

          {/* Status Badge */}
          <div className={`${badge.bg} rounded-md px-2 py-1 flex items-center`}>
            <span className={`text-[12px] font-normal ${badge.text} leading-[16px]`}>
              {badge.label}
            </span>
          </div>
        </div>
      </div>

      {/* Card Body */}
      <div className="p-4 flex flex-col flex-grow">
        <h3 className="text-[18px] font-semibold text-[#1A1A1A] leading-[27px] line-clamp-2">
          {title}
        </h3>
        <p className="text-[14px] font-normal text-[#757575] leading-[20px] mt-2">
          by {speaker}
        </p>

        <div className="flex flex-col gap-2 mt-4">
          <div className="flex items-center gap-2">
            <Calendar size={16} className="text-[#757575] shrink-0" />
            <span className="text-[14px] font-normal text-[#1A1A1A] leading-[20px]">{formattedDate}</span>
          </div>
          
          <div className="flex items-center gap-2">
            <Clock size={16} className="text-[#757575] shrink-0" />
            <span className="text-[14px] font-normal text-[#1A1A1A] leading-[20px]">{formattedStartTime} - {formattedEndTime}</span>
          </div>

          <div className="flex items-center gap-2">
            <Users size={16} className="text-[#757575] shrink-0" />
            <span className="text-[14px] font-normal text-[#1A1A1A] leading-[20px]">
              {capacity_remaining} of {capacity_total} seats available
            </span>
          </div>

          {price_amount > 0 && (
            <div className="flex items-center gap-2">
              <Tag size={16} className="text-primary shrink-0" />
              <span className="text-[14px] font-medium text-primary leading-[20px]">${price_amount}</span>
            </div>
          )}
        </div>

        {/* Urgency Row */}
        {workshopFrontendStatus === 'ALMOST_FULL' && (
          <div className="flex items-center gap-1 mt-auto pt-4">
            <Zap size={12} className="text-[#F57C00] shrink-0" />
            <span className="text-[12px] font-normal text-[#F57C00] leading-[16px]">High demand</span>
          </div>
        )}
      </div>
    </div>
  );
};
