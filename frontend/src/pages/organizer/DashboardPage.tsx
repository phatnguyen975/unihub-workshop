import React from 'react';
import { OrganizerLayout } from '../../layouts/OrganizerLayout';
import { Building2, Users, Calendar, BarChart } from 'lucide-react';

export const DashboardPage: React.FC = () => {
  return (
    <OrganizerLayout>
      <div className="flex flex-col gap-6">
        {/* Header Section */}
        <div>
          <h1 className="text-[30px] font-semibold text-[#1A1A1A] leading-[36px]">
            Dashboard
          </h1>
          <p className="text-[16px] text-[#757575] leading-[24px] mt-1">
            Overview of all organizations and workshops
          </p>
        </div>

        {/* Summary Cards */}
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
          {/* Total Organizations */}
          <div className="bg-white p-[17.33px] rounded-lg border-[1.33px] border-[#E0E0E0] shadow-sm flex flex-col gap-3 h-[86.67px]">
            <div className="flex items-center justify-between">
              <div className="flex flex-col">
                <span className="text-[#757575] text-[14px] leading-[20px]">Total Organizations</span>
                <span className="text-[#1A1A1A] text-[24px] leading-[32px] font-semibold">8</span>
              </div>
              <div className="w-12 h-12 bg-[#E8F5E9] rounded-lg flex items-center justify-center">
                <Building2 className="text-[#1B5E20]" size={24} />
              </div>
            </div>
          </div>
          
          {/* Total Workshops */}
          <div className="bg-white p-[17.33px] rounded-lg border-[1.33px] border-[#E0E0E0] shadow-sm flex flex-col gap-3 h-[86.67px]">
            <div className="flex items-center justify-between">
              <div className="flex flex-col">
                <span className="text-[#757575] text-[14px] leading-[20px]">Total Workshops</span>
                <span className="text-[#1A1A1A] text-[24px] leading-[32px] font-semibold">24</span>
              </div>
              <div className="w-12 h-12 bg-[#E8F5E9] rounded-lg flex items-center justify-center">
                <Calendar className="text-[#1B5E20]" size={24} />
              </div>
            </div>
          </div>

          {/* Total Students (Registrations) */}
          <div className="bg-white p-[17.33px] rounded-lg border-[1.33px] border-[#E0E0E0] shadow-sm flex flex-col gap-3 h-[86.67px]">
            <div className="flex items-center justify-between">
              <div className="flex flex-col">
                <span className="text-[#757575] text-[14px] leading-[20px]">Total Students</span>
                <span className="text-[#1A1A1A] text-[24px] leading-[32px] font-semibold">1,247</span>
              </div>
              <div className="w-12 h-12 bg-[#E8F5E9] rounded-lg flex items-center justify-center">
                <Users className="text-[#1B5E20]" size={24} />
              </div>
            </div>
          </div>

          {/* Total Assignments */}
          <div className="bg-white p-[17.33px] rounded-lg border-[1.33px] border-[#E0E0E0] shadow-sm flex flex-col gap-3 h-[86.67px]">
            <div className="flex items-center justify-between">
              <div className="flex flex-col">
                <span className="text-[#757575] text-[14px] leading-[20px]">Total Assignments</span>
                <span className="text-[#1A1A1A] text-[24px] leading-[32px] font-semibold">56</span>
              </div>
              <div className="w-12 h-12 bg-[#E8F5E9] rounded-lg flex items-center justify-center">
                <BarChart className="text-[#1B5E20]" size={24} />
              </div>
            </div>
          </div>
        </div>

        {/* Can add Recent Activity or upcoming workshops later if needed */}
        <div className="bg-white rounded-xl border border-[#E0E0E0] p-6 shadow-sm mt-4">
            <h2 className="text-[18px] font-semibold text-[#1A1A1A] mb-4">Quick Actions</h2>
            <div className="flex flex-wrap gap-4">
                <button className="px-4 py-2 border border-[#E0E0E0] rounded-lg text-[14px] font-medium text-[#1A1A1A] hover:bg-gray-50 transition-colors">
                    Manage Organizations
                </button>
                <button className="px-4 py-2 border border-[#E0E0E0] rounded-lg text-[14px] font-medium text-[#1A1A1A] hover:bg-gray-50 transition-colors">
                    Create New Workshop
                </button>
            </div>
        </div>

      </div>
    </OrganizerLayout>
  );
};
