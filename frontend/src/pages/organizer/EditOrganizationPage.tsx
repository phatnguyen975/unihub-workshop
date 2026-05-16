import React, { useState, useEffect } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { OrganizerLayout } from '../../layouts/OrganizerLayout';
import { MOCK_ORGANIZATIONS } from '../../constants/mockData';
import { SuccessModal } from '../../components/common/SuccessModal';
import { ArrowLeft, Save } from 'lucide-react';

export const EditOrganizationPage: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const [showSuccess, setShowSuccess] = useState(false);
  const [org, setOrg] = useState(MOCK_ORGANIZATIONS.find(o => o.id === id));

  useEffect(() => {
    if (!org) {
        navigate('/organizer/organizations');
    }
  }, [org, navigate]);

  if (!org) return null;

  const handleSave = (e: React.FormEvent) => {
    e.preventDefault();
    setShowSuccess(true);
  };

  return (
    <OrganizerLayout>
      <div className="flex flex-col gap-6 max-w-[800px]">
        {/* Breadcrumbs & Header */}
        <div>
          <div className="flex items-center gap-2 text-[14px] text-[#757575] mb-2">
            <button onClick={() => navigate('/organizer/organizations')} className="hover:text-[#1B5E20]">My Organizations</button>
            <span>/</span>
            <span className="text-[#1A1A1A] font-medium">Edit Organization</span>
          </div>
          <div className="flex items-center gap-4">
            <button 
              onClick={() => navigate(-1)}
              className="p-2 hover:bg-gray-100 rounded-full transition-colors"
            >
              <ArrowLeft size={24} className="text-[#1A1A1A]" />
            </button>
            <h1 className="text-[30px] font-semibold text-[#1A1A1A] leading-[36px]">
              Edit Organization
            </h1>
          </div>
        </div>

        {/* Edit Form */}
        <div className="bg-white rounded-2xl border-[1.33px] border-[#E0E0E0] shadow-sm overflow-hidden">
          <form onSubmit={handleSave} className="p-8 flex flex-col gap-6">
            <div className="flex flex-col gap-2">
              <label className="text-[14px] font-medium text-[#1A1A1A]">Organization Name *</label>
              <input 
                type="text" 
                required
                defaultValue={org.name}
                className="w-full h-[49px] px-4 rounded-lg border border-[#E0E0E0] bg-[#F5F5F5] focus:outline-none focus:border-[#1B5E20] text-[#1A1A1A]"
              />
            </div>

            <div className="flex flex-col gap-2">
              <label className="text-[14px] font-medium text-[#1A1A1A]">Description *</label>
              <textarea 
                required
                defaultValue={org.description}
                rows={6}
                className="w-full p-4 rounded-lg border border-[#E0E0E0] bg-[#F5F5F5] focus:outline-none focus:border-[#1B5E20] text-[#1A1A1A] resize-none"
              />
            </div>

            <div className="flex flex-col gap-2">
              <label className="text-[14px] font-medium text-[#1A1A1A]">Address *</label>
              <input 
                type="text" 
                required
                defaultValue={org.address}
                className="w-full h-[49px] px-4 rounded-lg border border-[#E0E0E0] bg-[#F5F5F5] focus:outline-none focus:border-[#1B5E20] text-[#1A1A1A]"
              />
            </div>

            <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
              <div className="flex flex-col gap-2">
                <label className="text-[14px] font-medium text-[#1A1A1A]">Contact Email *</label>
                <input 
                  type="email" 
                  required
                  defaultValue={`${org.initials.toLowerCase()}@university.edu`}
                  className="w-full h-[49px] px-4 rounded-lg border border-[#E0E0E0] bg-[#F5F5F5] focus:outline-none focus:border-[#1B5E20] text-[#1A1A1A]"
                />
              </div>
              <div className="flex flex-col gap-2">
                <label className="text-[14px] font-medium text-[#1A1A1A]">Contact Phone *</label>
                <input 
                  type="tel" 
                  required
                  defaultValue="+1 (555) 123-4567"
                  className="w-full h-[49px] px-4 rounded-lg border border-[#E0E0E0] bg-[#F5F5F5] focus:outline-none focus:border-[#1B5E20] text-[#1A1A1A]"
                />
              </div>
            </div>

            <div className="flex flex-col gap-2">
              <label className="text-[14px] font-medium text-[#1A1A1A]">Status</label>
              <select className="w-full h-[49px] px-4 rounded-lg border border-[#E0E0E0] bg-[#F5F5F5] focus:outline-none focus:border-[#1B5E20] text-[#1A1A1A]">
                <option value="ACTIVE" selected={org.status === 'ACTIVE'}>Active</option>
                <option value="INACTIVE" selected={org.status === 'INACTIVE'}>Inactive</option>
              </select>
            </div>

            <div className="pt-4 flex items-center justify-end gap-3 border-t border-[#E0E0E0]">
              <button 
                type="button"
                onClick={() => navigate(-1)}
                className="px-6 py-2.5 rounded-lg border border-[#E0E0E0] text-[#1A1A1A] font-medium text-[16px] hover:bg-gray-50 transition-colors"
              >
                Cancel
              </button>
              <button 
                type="submit"
                className="flex items-center gap-2 px-8 py-2.5 rounded-lg bg-[#1B5E20] hover:bg-[#144d18] text-white font-medium text-[16px] transition-colors"
              >
                <Save size={20} />
                Save Changes
              </button>
            </div>
          </form>
        </div>

        {/* Success Modal */}
        <SuccessModal 
          isOpen={showSuccess}
          onClose={() => {
            setShowSuccess(false);
            navigate('/organizer/organizations');
          }}
          title="Changes Saved!"
          message={`${org.name} has been updated successfully.`}
          buttonText="Return to Organizations"
        />
      </div>
    </OrganizerLayout>
  );
};
