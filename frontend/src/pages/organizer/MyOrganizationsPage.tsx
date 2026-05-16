import React, { useState } from 'react';
import { OrganizerLayout } from '../../layouts/OrganizerLayout';
import { MOCK_ORGANIZATIONS } from '../../constants/mockData';
import { MapPin, Plus, ChevronRight, Pencil } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import { CreateOrganizationModal } from '../../components/organizer/CreateOrganizationModal';

export const MyOrganizationsPage: React.FC = () => {
  const navigate = useNavigate();
  const [isCreateModalOpen, setIsCreateModalOpen] = useState(false);

  return (
    <OrganizerLayout>
      <div className="flex flex-col gap-6">
        {/* Header Section */}
        <div>
          <div className="flex items-center gap-2 text-[14px] text-[#757575] mb-2">
            <span>Organizer</span>
            <span>/</span>
            <span className="text-[#1A1A1A] font-medium">My Organizations</span>
          </div>
          <h1 className="text-[30px] font-semibold text-[#1A1A1A] leading-[36px]">
            My Organizations
          </h1>
          <p className="text-[16px] text-[#757575] leading-[24px] mt-1">
            Manage all organizational entities
          </p>
        </div>

        {/* Action Header */}
        <div className="flex items-center justify-between mt-2">
          <div className="flex flex-col gap-1">
            <h2 className="text-[24px] font-semibold text-[#1A1A1A]">Organization Management</h2>
            <p className="text-[14px] text-[#757575]">Create, view, and manage your organizations</p>
          </div>
          <button 
            onClick={() => setIsCreateModalOpen(true)}
            className="flex items-center justify-center gap-2 bg-[#1B5E20] hover:bg-[#144d18] text-white px-6 py-2.5 rounded-lg font-medium text-[16px] transition-colors whitespace-nowrap"
          >
            <Plus size={20} />
            Create Organization
          </button>
        </div>

        {/* Organizations List */}
        <div className="grid grid-cols-1 gap-6">
          {MOCK_ORGANIZATIONS.map((org) => (
            <div key={org.id} className="bg-white rounded-xl border-[1.33px] border-[#E0E0E0] shadow-sm overflow-hidden flex flex-col md:flex-row hover:border-[#1B5E20] transition-colors group">
              <div className="p-6 flex-1 flex flex-col md:flex-row gap-6 md:items-start">
                <div className="w-16 h-16 rounded-xl bg-[#E8F5E9] border border-[#C8E6C9] flex items-center justify-center shrink-0">
                  <span className="text-[#1B5E20] text-[20px] font-bold">{org.initials}</span>
                </div>
                
                <div className="flex-1 flex flex-col gap-3">
                  <div className="flex items-start justify-between">
                    <div>
                      <div className="flex items-center gap-3">
                        <h3 className="text-[18px] font-semibold text-[#1A1A1A]">{org.name}</h3>
                        <span className={`px-2 py-0.5 rounded-full text-[12px] font-medium ${
                          org.status === 'ACTIVE' ? 'bg-[#E8F5E9] text-[#2E7D32]' : 'bg-gray-100 text-gray-600'
                        }`}>
                          {org.status === 'ACTIVE' ? 'Active' : 'Inactive'}
                        </span>
                      </div>
                      <p className="text-[#757575] text-[14px] mt-1 leading-[20px]">{org.description}</p>
                    </div>
                    <button 
                      onClick={(e) => {
                        e.stopPropagation();
                        navigate(`/organizer/organizations/${org.id}/edit`);
                      }}
                      className="p-2 text-[#757575] hover:text-[#1B5E20] hover:bg-[#E8F5E9] rounded-lg transition-colors"
                    >
                      <Pencil size={18} />
                    </button>
                  </div>
                  
                  <div className="flex items-center gap-2 text-[#757575] text-[14px]">
                    <MapPin size={16} />
                    <span>{org.address}</span>
                  </div>
                </div>
                
                <div className="flex gap-6 md:px-6 md:border-l border-[#E0E0E0] shrink-0">
                  <div className="flex flex-col gap-1 text-center min-w-[80px]">
                    <span className="text-[#757575] text-[12px] font-medium uppercase tracking-wider">Workshops</span>
                    <span className="text-[#1A1A1A] text-[20px] font-semibold">{org.total_workshops}</span>
                  </div>
                  <div className="flex flex-col gap-1 text-center min-w-[60px]">
                    <span className="text-[#757575] text-[12px] font-medium uppercase tracking-wider">Staff</span>
                    <span className="text-[#1A1A1A] text-[20px] font-semibold">{org.staff_count}</span>
                  </div>
                  <div className="flex flex-col gap-1 text-center min-w-[80px]">
                    <span className="text-[#757575] text-[12px] font-medium uppercase tracking-wider">Students</span>
                    <span className="text-[#1A1A1A] text-[20px] font-semibold">{org.total_students.toLocaleString()}</span>
                  </div>
                </div>
              </div>
              
              <div className="bg-gray-50 p-4 border-t md:border-t-0 md:border-l border-[#E0E0E0] flex items-center justify-center shrink-0 w-full md:w-[140px] group-hover:bg-[#E8F5E9] transition-colors">
                <button 
                  onClick={() => navigate(`/organizer/organizations/${org.id}`)}
                  className="w-full h-full flex items-center justify-center gap-2 text-[#1B5E20] hover:text-[#144d18] font-semibold text-[14px]"
                >
                  View Details
                  <ChevronRight size={18} />
                </button>
              </div>
            </div>
          ))}
        </div>

        {/* Modals */}
        <CreateOrganizationModal 
          isOpen={isCreateModalOpen} 
          onClose={() => setIsCreateModalOpen(false)} 
        />
      </div>
    </OrganizerLayout>
  );
};
