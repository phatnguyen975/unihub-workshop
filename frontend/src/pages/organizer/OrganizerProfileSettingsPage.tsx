import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { OrganizerLayout } from '../../layouts/OrganizerLayout';
import { ArrowLeft, Check, Eye, EyeOff } from 'lucide-react';

export const OrganizerProfileSettingsPage: React.FC = () => {
  const navigate = useNavigate();
  const [activeTab, setActiveTab] = useState<'personal' | 'security'>('personal');
  const [showSuccess, setShowSuccess] = useState(false);

  // Form state
  const [fullName, setFullName] = useState('Admin Organizer');
  const [email, setEmail] = useState('admin@techuni.edu');
  const [phone, setPhone] = useState('+1 (555) 999-0001');

  // Password state
  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [showCurrentPassword, setShowCurrentPassword] = useState(false);
  const [showNewPassword, setShowNewPassword] = useState(false);
  const [showConfirmPassword, setShowConfirmPassword] = useState(false);

  const handleSave = (e: React.FormEvent) => {
    e.preventDefault();
    setShowSuccess(true);
    setTimeout(() => setShowSuccess(false), 3000);
  };

  return (
    <OrganizerLayout>
      <div className="flex flex-col gap-6">
        {/* Header Section */}
        <div>
          <button 
            onClick={() => navigate('/organizer')}
            className="flex items-center gap-2 text-[#1B5E20] hover:underline mb-[13px]"
          >
            <ArrowLeft size={20} />
            <span className="text-[16px] font-medium leading-[24px]">Back to Dashboard</span>
          </button>
          <h1 className="text-[30px] font-semibold text-[#1A1A1A] leading-[36px] mb-2 tracking-[-0.6px]">Profile Settings</h1>
          <p className="text-[16px] text-[#757575] leading-[24px]">Manage your personal information and security settings</p>
        </div>

        {/* Success Alert */}
        {showSuccess && (
          <div className="bg-[#C8E6C9] border border-[#2E7D32] rounded-lg p-4 flex items-center gap-3">
            <div className="w-5 h-5 bg-[#2E7D32] rounded-full flex items-center justify-center shrink-0">
              <Check size={12} className="text-white" strokeWidth={3} />
            </div>
            <p className="text-[#1B5E20] text-[16px] font-medium leading-[24px]">Changes saved successfully!</p>
          </div>
        )}

        {/* Content Section */}
        <div className="flex flex-col lg:flex-row gap-6 items-start">
          {/* Sidebar Tabs */}
          <div className="w-full lg:w-[271px] bg-white border border-[#E0E0E0] rounded-lg p-[17.33px] flex flex-col gap-2 shrink-0">
            <button
              onClick={() => setActiveTab('personal')}
              className={`w-full text-left px-4 py-3 rounded-lg text-[16px] font-medium transition-colors ${
                activeTab === 'personal' ? 'bg-[#E8F5E9] text-[#1B5E20]' : 'text-[#1A1A1A] hover:bg-gray-50'
              }`}
            >
              Personal Information
            </button>
            <button
              onClick={() => setActiveTab('security')}
              className={`w-full text-left px-4 py-3 rounded-lg text-[16px] font-medium transition-colors ${
                activeTab === 'security' ? 'bg-[#E8F5E9] text-[#1B5E20]' : 'text-[#1A1A1A] hover:bg-gray-50'
              }`}
            >
              Security
            </button>
          </div>

          {/* Main Form Area */}
          <div className="flex-1 w-full bg-white border border-[#E0E0E0] rounded-lg p-[25.33px]">
            {activeTab === 'personal' ? (
              <div className="flex flex-col gap-6">
                <h2 className="text-[20px] font-semibold text-[#1A1A1A] leading-[28px]">Personal Information</h2>
                
                <form onSubmit={handleSave} className="flex flex-col gap-6">
                  {/* Full Name */}
                  <div className="flex flex-col gap-2">
                    <label className="text-[14px] font-medium text-[#1A1A1A] leading-[20px]">Full Name</label>
                    <input
                      type="text"
                      value={fullName}
                      onChange={(e) => setFullName(e.target.value)}
                      className="w-full h-[50.67px] px-4 bg-[#F5F5F5] border border-[#E0E0E0] rounded-lg text-[#1A1A1A] text-[16px] focus:outline-none focus:ring-2 focus:ring-[#1B5E20]"
                    />
                  </div>

                  {/* Email */}
                  <div className="flex flex-col gap-2">
                    <label className="text-[14px] font-medium text-[#1A1A1A] leading-[20px]">Email Address</label>
                    <input
                      type="email"
                      value={email}
                      onChange={(e) => setEmail(e.target.value)}
                      className="w-full h-[50.67px] px-4 bg-[#F5F5F5] border border-[#E0E0E0] rounded-lg text-[#1A1A1A] text-[16px] focus:outline-none focus:ring-2 focus:ring-[#1B5E20]"
                    />
                  </div>

                  {/* Phone */}
                  <div className="flex flex-col gap-2">
                    <label className="text-[14px] font-medium text-[#1A1A1A] leading-[20px]">Phone Number</label>
                    <input
                      type="tel"
                      value={phone}
                      onChange={(e) => setPhone(e.target.value)}
                      className="w-full h-[50.67px] px-4 bg-[#F5F5F5] border border-[#E0E0E0] rounded-lg text-[#1A1A1A] text-[16px] focus:outline-none focus:ring-2 focus:ring-[#1B5E20]"
                    />
                  </div>

                  {/* Organization ID */}
                  <div className="flex flex-col gap-2">
                    <label className="text-[14px] font-medium text-[#1A1A1A] leading-[20px]">Organization ID</label>
                    <input
                      type="text"
                      value="ORG-2024-001"
                      disabled
                      className="w-full h-[50.67px] px-4 bg-[#EEEEEE] border border-[#E0E0E0] rounded-lg text-[#757575] text-[16px] cursor-not-allowed"
                    />
                    <p className="text-[12px] text-[#757575] leading-[16px]">Organization ID cannot be changed</p>
                  </div>

                  {/* Submit Button */}
                  <div className="pt-4">
                    <button
                      type="submit"
                      className="h-[48px] px-8 bg-[#1B5E20] text-white font-medium text-[16px] rounded-lg hover:bg-[#154a19] transition-colors"
                    >
                      Update Profile
                    </button>
                  </div>
                </form>
              </div>
            ) : (
              <div className="flex flex-col gap-6">
                <h2 className="text-[20px] font-semibold text-[#1A1A1A] leading-[28px]">Security Settings</h2>
                
                <form onSubmit={handleSave} className="flex flex-col gap-6">
                  {/* Current Password */}
                  <div className="flex flex-col gap-2">
                    <label className="text-[14px] font-medium text-[#1A1A1A] leading-[20px]">Current Password</label>
                    <div className="relative">
                      <input
                        type={showCurrentPassword ? "text" : "password"}
                        value={currentPassword}
                        onChange={(e) => setCurrentPassword(e.target.value)}
                        className="w-full h-[50.67px] px-4 bg-[#F5F5F5] border border-[#E0E0E0] rounded-lg text-[#1A1A1A] text-[16px] focus:outline-none focus:ring-2 focus:ring-[#1B5E20]"
                      />
                      <button
                        type="button"
                        onClick={() => setShowCurrentPassword(!showCurrentPassword)}
                        className="absolute right-4 top-1/2 -translate-y-1/2 text-[#757575] hover:text-[#1A1A1A]"
                      >
                        {showCurrentPassword ? <EyeOff size={20} /> : <Eye size={20} />}
                      </button>
                    </div>
                  </div>

                  {/* New Password */}
                  <div className="flex flex-col gap-2">
                    <label className="text-[14px] font-medium text-[#1A1A1A] leading-[20px]">New Password</label>
                    <div className="relative">
                      <input
                        type={showNewPassword ? "text" : "password"}
                        value={newPassword}
                        onChange={(e) => setNewPassword(e.target.value)}
                        className="w-full h-[50.67px] px-4 bg-[#F5F5F5] border border-[#E0E0E0] rounded-lg text-[#1A1A1A] text-[16px] focus:outline-none focus:ring-2 focus:ring-[#1B5E20]"
                      />
                      <button
                        type="button"
                        onClick={() => setShowNewPassword(!showNewPassword)}
                        className="absolute right-4 top-1/2 -translate-y-1/2 text-[#757575] hover:text-[#1A1A1A]"
                      >
                        {showNewPassword ? <EyeOff size={20} /> : <Eye size={20} />}
                      </button>
                    </div>
                  </div>

                  {/* Confirm Password */}
                  <div className="flex flex-col gap-2">
                    <label className="text-[14px] font-medium text-[#1A1A1A] leading-[20px]">Confirm Password</label>
                    <div className="relative">
                      <input
                        type={showConfirmPassword ? "text" : "password"}
                        value={confirmPassword}
                        onChange={(e) => setConfirmPassword(e.target.value)}
                        className="w-full h-[50.67px] px-4 bg-[#F5F5F5] border border-[#E0E0E0] rounded-lg text-[#1A1A1A] text-[16px] focus:outline-none focus:ring-2 focus:ring-[#1B5E20]"
                      />
                      <button
                        type="button"
                        onClick={() => setShowConfirmPassword(!showConfirmPassword)}
                        className="absolute right-4 top-1/2 -translate-y-1/2 text-[#757575] hover:text-[#1A1A1A]"
                      >
                        {showConfirmPassword ? <EyeOff size={20} /> : <Eye size={20} />}
                      </button>
                    </div>
                  </div>

                  {/* Password Requirements */}
                  <div className="bg-[#E3F2FD] border border-[#90CAF9] rounded-lg p-4 flex flex-col gap-1.5 mt-2">
                    <h3 className="text-[14px] font-semibold text-[#0D47A1] leading-[20px] mb-0.5">Password Requirements:</h3>
                    <ul className="flex flex-col gap-1 pl-4 list-disc marker:text-[#1565C0]">
                      <li className="text-[12px] text-[#1565C0] leading-[16px]">Minimum 8 characters long</li>
                      <li className="text-[12px] text-[#1565C0] leading-[16px]">Include uppercase and lowercase letters</li>
                      <li className="text-[12px] text-[#1565C0] leading-[16px]">Include at least one number</li>
                      <li className="text-[12px] text-[#1565C0] leading-[16px]">Include at least one special character</li>
                    </ul>
                  </div>

                  {/* Submit Button */}
                  <div className="pt-4">
                    <button
                      type="submit"
                      className="h-[48px] px-8 bg-[#1B5E20] text-white font-medium text-[16px] rounded-lg hover:bg-[#154a19] transition-colors"
                    >
                      Change Password
                    </button>
                  </div>
                </form>
              </div>
            )}
          </div>
        </div>
      </div>
    </OrganizerLayout>
  );
};
