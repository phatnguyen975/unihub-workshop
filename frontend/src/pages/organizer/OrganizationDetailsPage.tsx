import React, { useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { OrganizerLayout } from '../../layouts/OrganizerLayout';
import { MOCK_ORGANIZATIONS } from '../../constants/mockData';
import { ArrowLeft, MapPin, Mail, Phone, Calendar, Users, BookOpen, Trash2, Pencil } from 'lucide-react';
import { ConfirmModal } from '../../components/common/ConfirmModal';

export const OrganizationDetailsPage: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  
  const org = MOCK_ORGANIZATIONS.find(o => o.id === id) || MOCK_ORGANIZATIONS[0];

  const [activeTab, setActiveTab] = useState<'workshops' | 'staff'>('workshops');
  const [isRemovingWorkshop, setIsRemovingWorkshop] = useState(false);
  const [isRemovingStaff, setIsRemovingStaff] = useState(false);
  const [isDeletingOrg, setIsDeletingOrg] = useState(false);

  return (
    <OrganizerLayout>
      <div className="flex flex-col gap-6">
        {/* Top Navigation & Header */}
        <div>
          <div className="flex items-center gap-2 text-[14px] text-[#757575] mb-2">
            <button onClick={() => navigate('/organizer/organizations')} className="hover:text-[#1B5E20]">My Organizations</button>
            <span>/</span>
            <span className="text-[#1A1A1A] font-medium">{org.name}</span>
          </div>
          
          <div className="flex flex-col md:flex-row md:items-center justify-between gap-4">
            <div className="flex items-center gap-4">
              <button 
                onClick={() => navigate(-1)}
                className="p-2 hover:bg-gray-100 rounded-full transition-colors"
              >
                <ArrowLeft size={24} className="text-[#1A1A1A]" />
              </button>
              <div className="flex items-center gap-3">
                <h1 className="text-[30px] font-semibold text-[#1A1A1A] leading-[36px]">
                  {org.name}
                </h1>
                <span className={`px-2.5 py-1 rounded-full text-[12px] font-medium ${
                  org.status === 'ACTIVE' ? 'bg-[#E8F5E9] text-[#2E7D32]' : 'bg-gray-100 text-gray-600'
                }`}>
                  {org.status === 'ACTIVE' ? 'Active' : 'Inactive'}
                </span>
              </div>
            </div>
            
            <div className="flex items-center gap-3">
              <button 
                onClick={() => navigate(`/organizer/organizations/${org.id}/edit`)}
                className="flex items-center gap-2 px-4 py-2 border-[1.33px] border-[#E0E0E0] rounded-lg text-[#1A1A1A] font-medium text-[14px] hover:bg-gray-50 transition-colors"
              >
                <Pencil size={16} />
                Edit Organization
              </button>
              <button 
                onClick={() => setIsDeletingOrg(true)}
                className="flex items-center gap-2 px-4 py-2 border-[1.33px] border-red-200 text-red-600 rounded-lg font-medium text-[14px] hover:bg-red-50 transition-colors"
              >
                <Trash2 size={16} />
                Delete
              </button>
            </div>
          </div>
        </div>

        {/* Info & Stats Grid */}
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
          {/* Organization Information */}
          <div className="lg:col-span-2 bg-white rounded-2xl border-[1.33px] border-[#E0E0E0] shadow-sm p-8 flex flex-col gap-6">
            <h2 className="text-[24px] font-semibold text-[#1A1A1A]">Organization Information</h2>
            <p className="text-[16px] text-[#757575] leading-[24px]">{org.description}</p>
            
            <div className="grid grid-cols-1 md:grid-cols-2 gap-y-6 gap-x-8 mt-2">
              <div className="flex items-start gap-3">
                <div className="w-10 h-10 bg-[#F5F5F5] rounded-lg flex items-center justify-center shrink-0">
                    <MapPin className="text-[#1B5E20]" size={20} />
                </div>
                <div>
                  <p className="text-[12px] text-[#757575] font-medium uppercase tracking-wider">Address</p>
                  <p className="text-[15px] text-[#1A1A1A] font-medium mt-0.5">{org.address}</p>
                </div>
              </div>
              <div className="flex items-start gap-3">
                <div className="w-10 h-10 bg-[#F5F5F5] rounded-lg flex items-center justify-center shrink-0">
                    <Mail className="text-[#1B5E20]" size={20} />
                </div>
                <div>
                  <p className="text-[12px] text-[#757575] font-medium uppercase tracking-wider">Contact Email</p>
                  <p className="text-[15px] text-[#1A1A1A] font-medium mt-0.5">{org.contact_email || 'contact@org.edu'}</p>
                </div>
              </div>
              <div className="flex items-start gap-3">
                <div className="w-10 h-10 bg-[#F5F5F5] rounded-lg flex items-center justify-center shrink-0">
                    <Phone className="text-[#1B5E20]" size={20} />
                </div>
                <div>
                  <p className="text-[12px] text-[#757575] font-medium uppercase tracking-wider">Contact Phone</p>
                  <p className="text-[15px] text-[#1A1A1A] font-medium mt-0.5">{org.contact_phone || '+1 (555) 000-0000'}</p>
                </div>
              </div>
              <div className="flex items-start gap-3">
                <div className="w-10 h-10 bg-[#F5F5F5] rounded-lg flex items-center justify-center shrink-0">
                    <Calendar className="text-[#1B5E20]" size={20} />
                </div>
                <div>
                  <p className="text-[12px] text-[#757575] font-medium uppercase tracking-wider">Member Since</p>
                  <p className="text-[15px] text-[#1A1A1A] font-medium mt-0.5">{new Date(org.created_at).toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' })}</p>
                </div>
              </div>
            </div>
          </div>

          {/* Statistics */}
          <div className="bg-white rounded-2xl border-[1.33px] border-[#E0E0E0] shadow-sm p-8 flex flex-col gap-6">
            <h2 className="text-[24px] font-semibold text-[#1A1A1A]">Quick Statistics</h2>
            
            <div className="flex items-center justify-between p-5 bg-[#E8F5E9] rounded-xl border border-[#C8E6C9]">
              <div className="flex items-center gap-3">
                <div className="w-12 h-12 bg-white rounded-lg flex items-center justify-center shadow-sm">
                  <Users className="text-[#1B5E20]" size={24} />
                </div>
                <span className="text-[16px] font-semibold text-[#1B5E20]">Staff Members</span>
              </div>
              <span className="text-[28px] font-bold text-[#1B5E20]">{org.staff_count}</span>
            </div>

            <div className="flex items-center justify-between p-5 bg-[#E3F2FD] rounded-xl border border-[#BBDEFB]">
              <div className="flex items-center gap-3">
                <div className="w-12 h-12 bg-white rounded-lg flex items-center justify-center shadow-sm">
                  <BookOpen className="text-[#1976D2]" size={24} />
                </div>
                <span className="text-[16px] font-semibold text-[#1976D2]">Total Workshops</span>
              </div>
              <span className="text-[28px] font-bold text-[#1976D2]">{org.total_workshops}</span>
            </div>
          </div>
        </div>

        {/* Tab Selection */}
        <div className="flex border-b-[1.33px] border-[#E0E0E0] mt-4">
          <button 
            onClick={() => setActiveTab('workshops')}
            className={`px-8 py-4 font-semibold text-[16px] border-b-[3px] transition-colors ${activeTab === 'workshops' ? 'border-[#1B5E20] text-[#1B5E20]' : 'border-transparent text-[#757575] hover:text-[#1A1A1A]'}`}
          >
            Workshops Assigned
          </button>
          <button 
            onClick={() => setActiveTab('staff')}
            className={`px-8 py-4 font-semibold text-[16px] border-b-[3px] transition-colors ${activeTab === 'staff' ? 'border-[#1B5E20] text-[#1B5E20]' : 'border-transparent text-[#757575] hover:text-[#1A1A1A]'}`}
          >
            Staff Members
          </button>
        </div>

        {/* Tab Content */}
        <div className="bg-white rounded-2xl border-[1.33px] border-[#E0E0E0] shadow-sm overflow-hidden flex flex-col mb-10">
          <div className="p-8 border-b border-[#E0E0E0] flex flex-col md:flex-row md:items-center justify-between gap-4">
            <div>
              <h2 className="text-[24px] font-semibold text-[#1A1A1A]">
                {activeTab === 'workshops' ? 'Workshops assigned to this organization' : 'Staff members assigned to this organization'}
              </h2>
              <p className="text-[14px] text-[#757575] mt-1">
                {activeTab === 'workshops' ? 'Manage workshops and their statuses under this organization.' : 'Manage staff members and their active assignments.'}
              </p>
            </div>
            <button className="px-6 py-2.5 bg-[#1B5E20] text-white rounded-lg font-semibold text-[16px] hover:bg-[#144d18] transition-colors whitespace-nowrap">
              {activeTab === 'workshops' ? 'Manage Workshops Assignment' : 'Manage Staffs Assignment'}
            </button>
          </div>
          
          <div className="overflow-x-auto">
            <table className="w-full text-left border-collapse">
              <thead>
                <tr className="bg-[#F9FAFB] border-b border-[#E0E0E0]">
                  {activeTab === 'workshops' ? (
                    <>
                      <th className="p-6 text-[12px] font-bold text-[#757575] uppercase tracking-wider">Workshop Title</th>
                      <th className="p-6 text-[12px] font-bold text-[#757575] uppercase tracking-wider">Location</th>
                      <th className="p-6 text-[12px] font-bold text-[#757575] uppercase tracking-wider">Capacity</th>
                      <th className="p-6 text-[12px] font-bold text-[#757575] uppercase tracking-wider text-right">Actions</th>
                    </>
                  ) : (
                    <>
                      <th className="p-6 text-[12px] font-bold text-[#757575] uppercase tracking-wider">Name</th>
                      <th className="p-6 text-[12px] font-bold text-[#757575] uppercase tracking-wider">Contact Info</th>
                      <th className="p-6 text-[12px] font-bold text-[#757575] uppercase tracking-wider">Assignments</th>
                      <th className="p-6 text-[12px] font-bold text-[#757575] uppercase tracking-wider text-right">Actions</th>
                    </>
                  )}
                </tr>
              </thead>
              <tbody className="divide-y divide-[#E0E0E0]">
                {activeTab === 'workshops' ? (
                  <tr className="hover:bg-gray-50 transition-colors">
                    <td className="p-6">
                      <div className="font-semibold text-[#1A1A1A] text-[15px]">Machine Learning Fundamentals</div>
                      <div className="mt-1 flex items-center gap-2">
                        <span className="w-2 h-2 rounded-full bg-green-500"></span>
                        <span className="text-[#1B5E20] text-[12px] font-medium">Published</span>
                      </div>
                    </td>
                    <td className="p-6 text-[14px] text-[#1A1A1A]">Engineering Hall, Room 201</td>
                    <td className="p-6 text-[14px] text-[#1A1A1A]">50 / 50</td>
                    <td className="p-6 text-right">
                      <button 
                        onClick={() => setIsRemovingWorkshop(true)}
                        className="text-red-600 hover:text-red-800 font-semibold text-[14px] px-3 py-1.5 hover:bg-red-50 rounded-lg transition-colors"
                      >
                        Remove
                      </button>
                    </td>
                  </tr>
                ) : (
                  <tr className="hover:bg-gray-50 transition-colors">
                    <td className="p-6 flex items-center gap-3">
                      <div className="w-10 h-10 rounded-full bg-[#E8F5E9] text-[#1B5E20] flex items-center justify-center font-bold text-[14px]">MC</div>
                      <div>
                        <span className="font-semibold text-[#1A1A1A] text-[15px]">Michael Chen</span>
                        <p className="text-[12px] text-[#757575]">Senior Instructor</p>
                      </div>
                    </td>
                    <td className="p-6 text-[14px] text-[#757575]">michael.chen@techuni.edu</td>
                    <td className="p-6 text-[14px] text-[#1A1A1A]">3 Active Workshops</td>
                    <td className="p-6 text-right">
                      <button 
                        onClick={() => setIsRemovingStaff(true)}
                        className="text-red-600 hover:text-red-800 font-semibold text-[14px] px-3 py-1.5 hover:bg-red-50 rounded-lg transition-colors"
                      >
                        Remove
                      </button>
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
        </div>

        {/* Confirmation Modals */}
        <ConfirmModal 
          isOpen={isRemovingWorkshop}
          onClose={() => setIsRemovingWorkshop(false)}
          onConfirm={() => console.log('Workshop removed')}
          title="Remove Workshop?"
          message="Are you sure you want to remove this workshop from the organization? This action cannot be undone."
          confirmText="Remove Workshop"
        />

        <ConfirmModal 
          isOpen={isRemovingStaff}
          onClose={() => setIsRemovingStaff(false)}
          onConfirm={() => console.log('Staff removed')}
          title="Remove Staff Member?"
          message="Are you sure you want to remove Michael Chen from this organization? They will no longer be able to manage workshops assigned here."
          confirmText="Remove Staff"
        />

        <ConfirmModal 
          isOpen={isDeletingOrg}
          onClose={() => setIsDeletingOrg(false)}
          onConfirm={() => {
            console.log('Org deleted');
            navigate('/organizer/organizations');
          }}
          title="Delete Organization?"
          message={`Are you sure you want to delete ${org.name}? All associated data will be permanently removed.`}
          confirmText="Delete Organization"
        />

      </div>
    </OrganizerLayout>
  );
};

