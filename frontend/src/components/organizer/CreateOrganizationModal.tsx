import React, { useState } from 'react';
import { X } from 'lucide-react';
import { SuccessModal } from '../common/SuccessModal';

interface CreateOrganizationModalProps {
  isOpen: boolean;
  onClose: () => void;
}

export const CreateOrganizationModal: React.FC<CreateOrganizationModalProps> = ({ isOpen, onClose }) => {
  const [showSuccess, setShowSuccess] = useState(false);
  const [orgName, setOrgName] = useState('');

  if (!isOpen) return null;

  const handleCreate = (e: React.FormEvent) => {
    e.preventDefault();
    setShowSuccess(true);
  };

  const handleCloseSuccess = () => {
    setShowSuccess(false);
    onClose();
  };

  if (showSuccess) {
    return (
      <SuccessModal
        isOpen={true}
        onClose={handleCloseSuccess}
        title="Organization Created!"
        message={`${orgName || 'Tech Uni'} has been created successfully. You can now manage its workshops and staff.`}
        buttonText="View Organizations"
      />
    );
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4">
      <div className="bg-white rounded-2xl shadow-2xl w-full max-w-[672px] overflow-hidden flex flex-col max-h-[90vh]">
        {/* Header */}
        <div className="px-8 py-6 border-b border-[#E0E0E0] flex items-center justify-between shrink-0">
          <h2 className="text-[24px] font-semibold text-[#1A1A1A]">Create New Organization</h2>
          <button
            onClick={onClose}
            className="w-10 h-10 flex items-center justify-center text-[#757575] hover:bg-gray-100 rounded-full transition-colors"
          >
            <X size={24} />
          </button>
        </div>

        {/* Form Body */}
        <div className="p-8 overflow-y-auto flex-1">
          <form onSubmit={handleCreate} className="flex flex-col gap-6">
            <div className="flex flex-col gap-2">
              <label className="text-[14px] font-medium text-[#1A1A1A]">Organization Name *</label>
              <input
                type="text"
                required
                value={orgName}
                onChange={(e) => setOrgName(e.target.value)}
                placeholder="e.g., Tech University"
                className="w-full h-[49px] px-4 rounded-lg border border-[#E0E0E0] bg-[#F5F5F5] focus:outline-none focus:border-[#1B5E20] text-[#1A1A1A] placeholder-[#1A1A1A]/50"
              />
            </div>

            <div className="flex flex-col gap-2">
              <label className="text-[14px] font-medium text-[#1A1A1A]">Description *</label>
              <textarea
                required
                placeholder="Brief description of the organization"
                rows={4}
                className="w-full p-4 rounded-lg border border-[#E0E0E0] bg-[#F5F5F5] focus:outline-none focus:border-[#1B5E20] text-[#1A1A1A] placeholder-[#1A1A1A]/50 resize-none"
              />
            </div>

            <div className="flex flex-col gap-2">
              <label className="text-[14px] font-medium text-[#1A1A1A]">Address *</label>
              <input
                type="text"
                required
                placeholder="Full address"
                className="w-full h-[49px] px-4 rounded-lg border border-[#E0E0E0] bg-[#F5F5F5] focus:outline-none focus:border-[#1B5E20] text-[#1A1A1A] placeholder-[#1A1A1A]/50"
              />
            </div>

            <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
              <div className="flex flex-col gap-2">
                <label className="text-[14px] font-medium text-[#1A1A1A]">Contact Email *</label>
                <input
                  type="email"
                  required
                  placeholder="contact@organization.edu"
                  className="w-full h-[49px] px-4 rounded-lg border border-[#E0E0E0] bg-[#F5F5F5] focus:outline-none focus:border-[#1B5E20] text-[#1A1A1A] placeholder-[#1A1A1A]/50"
                />
              </div>
              <div className="flex flex-col gap-2">
                <label className="text-[14px] font-medium text-[#1A1A1A]">Contact Phone *</label>
                <input
                  type="tel"
                  required
                  placeholder="+1 (555) 000-0000"
                  className="w-full h-[49px] px-4 rounded-lg border border-[#E0E0E0] bg-[#F5F5F5] focus:outline-none focus:border-[#1B5E20] text-[#1A1A1A] placeholder-[#1A1A1A]/50"
                />
              </div>
            </div>

            {/* Footer buttons inside form for submit action */}
            <div className="mt-4 flex items-center justify-end gap-3 shrink-0">
              <button
                type="button"
                onClick={onClose}
                className="px-6 py-2.5 rounded-lg border border-[#E0E0E0] text-[#1A1A1A] font-medium text-[16px] hover:bg-gray-50 transition-colors"
              >
                Cancel
              </button>
              <button
                type="submit"
                className="px-6 py-2.5 rounded-lg bg-[#1B5E20] hover:bg-[#144d18] text-white font-medium text-[16px] transition-colors"
              >
                Create Organization
              </button>
            </div>
          </form>
        </div>
      </div>
    </div>
  );
};


