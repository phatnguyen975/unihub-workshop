import React, { useState } from 'react';
import { User, Shield, Sliders, CheckCircle } from 'lucide-react';
import { StudentHeader } from '../../layouts/StudentHeader';
import { useAuthStore } from '../../hooks/useAuthStore';

type Tab = 'PERSONAL' | 'SECURITY' | 'PREFERENCES';

export const ProfileSettingsPage: React.FC = () => {
  const user = useAuthStore((state) => state.user);
  const [activeTab, setActiveTab] = useState<Tab>('PERSONAL');
  const [isUpdated, setIsUpdated] = useState(false);

  // Form states for Personal Info
  const [fullName, setFullName] = useState(user?.full_name || 'Sarah Duyen');
  const [email, setEmail] = useState(user?.email || 'sarah.johnson@university.edu');
  const [phone, setPhone] = useState(user?.phone || '+1 (555) 123-4567');

  // Form states for Security
  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');

  const handleUpdateProfile = (e: React.FormEvent) => {
    e.preventDefault();
    // Simulate API update
    setIsUpdated(true);
    setTimeout(() => setIsUpdated(false), 3000);
  };

  const getInitials = (name: string) => {
    return name
      .split(' ')
      .map((n) => n[0])
      .join('')
      .toUpperCase()
      .substring(0, 2);
  };

  const initials = user?.full_name ? getInitials(user.full_name) : 'SD';

  return (
    <div className="min-h-screen bg-[#FAFAFA] flex flex-col relative">
      <div className="fixed top-0 left-0 right-0 z-40">
        <StudentHeader />
      </div>

      <main className="flex-1 w-full flex flex-col items-center pt-[104px] px-[90px] pb-[80px]">
        <div className="w-full max-w-[1280px] flex flex-col gap-[32px]">
          
          {/* Header */}
          <div className="flex flex-col gap-2">
            <h1 className="text-[30px] font-semibold text-[#1A1A1A] leading-[36px]">
              Profile Settings
            </h1>
            <p className="text-[16px] font-normal text-[#757575] leading-[24px]">
              Manage your account information and security settings
            </p>
          </div>

          <div className="flex flex-col lg:flex-row gap-[24px]">
            
            {/* Left Sidebar */}
            <div className="w-full lg:w-[400px] flex flex-col">
              <div className="bg-white border-[1.33px] border-[#E0E0E0] rounded-lg p-[24px] flex flex-col gap-[24px]">
                
                {/* Avatar & Info */}
                <div className="flex flex-col items-center border-b-[1.33px] border-[#E0E0E0] pb-6">
                  <div className="w-[96px] h-[96px] rounded-full flex items-center justify-center text-white font-semibold text-[36px] mb-4 shadow-inner" style={{ background: 'linear-gradient(135deg, #1B5E20 0%, #43A047 100%)', border: '4px solid #FFFFFF' }}>
                    {initials}
                  </div>
                  <h3 className="text-[18px] font-semibold text-[#1A1A1A] leading-[27px] text-center">
                    {user?.full_name || 'Sarah Duyen'}
                  </h3>
                  <p className="text-[14px] font-normal text-[#757575] leading-[20px] text-center mt-1">
                    {user?.student_id || 'STU-2024-1001'}
                  </p>
                </div>

                {/* Navigation */}
                <div className="flex flex-col gap-1">
                  <button 
                    onClick={() => setActiveTab('PERSONAL')}
                    className={`flex items-center w-full h-[40px] px-4 rounded-lg transition-colors ${activeTab === 'PERSONAL' ? 'bg-[#E8F5E9] text-primary font-medium' : 'text-[#1A1A1A] font-normal hover:bg-gray-50'}`}
                  >
                    <span className="text-[16px] leading-[24px]">Personal Information</span>
                  </button>
                  <button 
                    onClick={() => setActiveTab('SECURITY')}
                    className={`flex items-center w-full h-[40px] px-4 rounded-lg transition-colors ${activeTab === 'SECURITY' ? 'bg-[#E8F5E9] text-primary font-medium' : 'text-[#1A1A1A] font-normal hover:bg-gray-50'}`}
                  >
                    <span className="text-[16px] leading-[24px]">Security</span>
                  </button>
                  <button 
                    onClick={() => setActiveTab('PREFERENCES')}
                    className={`flex items-center w-full h-[40px] px-4 rounded-lg transition-colors ${activeTab === 'PREFERENCES' ? 'bg-[#E8F5E9] text-primary font-medium' : 'text-[#1A1A1A] font-normal hover:bg-gray-50'}`}
                  >
                    <span className="text-[16px] leading-[24px]">Preferences</span>
                  </button>
                </div>

              </div>

              {/* Student Experience badge bottom left */}
              <div className="mt-auto pt-6">
                <div className="inline-flex items-center gap-2 bg-white border-[1.33px] border-[#E0E0E0] shadow-[0px_10px_15px_-3px_rgba(0,0,0,0.1),0px_4px_6px_-4px_rgba(0,0,0,0.1)] rounded-lg py-2 px-4">
                  <div className="w-3 h-3 bg-primary rounded-full"></div>
                  <span className="text-[14px] font-medium text-[#1A1A1A]">Student Experience</span>
                  <Sliders size={16} className="text-[#757575] ml-2" />
                </div>
              </div>
            </div>

            {/* Right Content */}
            <div className="w-full lg:flex-1">
              
              {activeTab === 'PERSONAL' && (
                <div className="bg-white border-[1.33px] border-[#E0E0E0] rounded-lg p-[25.33px] flex flex-col gap-[24px]">
                  <div className="flex flex-col gap-1">
                    <h2 className="text-[20px] font-semibold text-[#1A1A1A] leading-[30px]">Personal Information</h2>
                    <p className="text-[14px] font-normal text-[#757575] leading-[20px]">Update your personal details and contact information</p>
                  </div>

                  {isUpdated && (
                    <div className="bg-[#C8E6C9] border-[1.33px] border-[#81C784] rounded-lg p-4 flex items-center gap-3">
                      <CheckCircle className="text-[#2E7D32] w-5 h-5" />
                      <span className="text-[14px] font-medium text-[#2E7D32]">Profile updated successfully!</span>
                    </div>
                  )}

                  <form onSubmit={handleUpdateProfile} className="flex flex-col gap-[16px]">
                    {/* Full Name */}
                    <div className="flex flex-col gap-2">
                      <label className="text-[14px] font-medium text-[#1A1A1A] leading-[20px]">Full Name</label>
                      <div className="relative">
                        <User className="absolute left-4 top-1/2 -translate-y-1/2 text-[#757575] w-5 h-5" />
                        <input
                          type="text"
                          value={fullName}
                          onChange={(e) => setFullName(e.target.value)}
                          className="w-full bg-[#F5F5F5] border-[1.33px] border-[#E0E0E0] rounded-lg h-[50.67px] pl-[40px] pr-4 text-[16px] text-[#1A1A1A] focus:outline-none focus:border-primary focus:ring-1 focus:ring-primary transition-colors"
                        />
                      </div>
                    </div>

                    {/* Student ID (Disabled) */}
                    <div className="flex flex-col gap-2">
                      <label className="text-[14px] font-medium text-[#1A1A1A] leading-[20px]">Student ID</label>
                      <div className="relative">
                        <Shield className="absolute left-4 top-1/2 -translate-y-1/2 text-[#757575] w-5 h-5" />
                        <input
                          type="text"
                          value={user?.student_id || 'STU-2024-1001'}
                          disabled
                          className="w-full bg-[#EEEEEE] border-[1.33px] border-[#E0E0E0] rounded-lg h-[50.67px] pl-[40px] pr-4 text-[16px] text-[#757575] cursor-not-allowed"
                        />
                      </div>
                      <span className="text-[12px] text-[#757575] leading-[16px]">Student ID cannot be changed</span>
                    </div>

                    {/* Email Address */}
                    <div className="flex flex-col gap-2">
                      <label className="text-[14px] font-medium text-[#1A1A1A] leading-[20px]">Email Address</label>
                      <div className="relative">
                        <svg className="absolute left-4 top-1/2 -translate-y-1/2 text-[#757575] w-5 h-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><rect x="2" y="4" width="20" height="16" rx="2"/><path d="m22 7-8.97 5.7a1.94 1.94 0 0 1-2.06 0L2 7"/></svg>
                        <input
                          type="email"
                          value={email}
                          onChange={(e) => setEmail(e.target.value)}
                          className="w-full bg-[#F5F5F5] border-[1.33px] border-[#E0E0E0] rounded-lg h-[50.67px] pl-[40px] pr-4 text-[16px] text-[#1A1A1A] focus:outline-none focus:border-primary focus:ring-1 focus:ring-primary transition-colors"
                        />
                      </div>
                    </div>

                    {/* Phone Number */}
                    <div className="flex flex-col gap-2 mb-2">
                      <label className="text-[14px] font-medium text-[#1A1A1A] leading-[20px]">Phone Number</label>
                      <div className="relative">
                        <svg className="absolute left-4 top-1/2 -translate-y-1/2 text-[#757575] w-5 h-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M22 16.92v3a2 2 0 0 1-2.18 2 19.79 19.79 0 0 1-8.63-3.07 19.5 19.5 0 0 1-6-6 19.79 19.79 0 0 1-3.07-8.67A2 2 0 0 1 4.11 2h3a2 2 0 0 1 2 1.72 12.84 12.84 0 0 0 .7 2.81 2 2 0 0 1-.45 2.11L8.09 9.91a16 16 0 0 0 6 6l1.27-1.27a2 2 0 0 1 2.11-.45 12.84 12.84 0 0 0 2.81.7A2 2 0 0 1 22 16.92z"/></svg>
                        <input
                          type="tel"
                          value={phone}
                          onChange={(e) => setPhone(e.target.value)}
                          className="w-full bg-[#F5F5F5] border-[1.33px] border-[#E0E0E0] rounded-lg h-[50.67px] pl-[40px] pr-4 text-[16px] text-[#1A1A1A] focus:outline-none focus:border-primary focus:ring-1 focus:ring-primary transition-colors"
                        />
                      </div>
                    </div>

                    <button type="submit" className="h-[48px] bg-primary text-white rounded-lg text-[16px] font-medium hover:bg-[#154d19] transition-colors w-full sm:w-auto px-8 self-start">
                      Update Profile
                    </button>
                  </form>
                </div>
              )}

              {activeTab === 'SECURITY' && (
                <div className="flex flex-col gap-[24px]">
                  
                  {/* Security Settings */}
                  <div className="bg-white border-[1.33px] border-[#E0E0E0] rounded-lg p-[25.33px] flex flex-col gap-[24px]">
                    <div className="flex flex-col gap-1">
                      <h2 className="text-[20px] font-semibold text-[#1A1A1A] leading-[30px]">Security Settings</h2>
                      <p className="text-[14px] font-normal text-[#757575] leading-[20px]">Change your password to keep your account secure</p>
                    </div>

                    <form className="flex flex-col gap-[16px]">
                      {/* Current Password */}
                      <div className="flex flex-col gap-2">
                        <label className="text-[14px] font-medium text-[#1A1A1A] leading-[20px]">Current Password</label>
                        <div className="relative">
                          <Shield className="absolute left-4 top-1/2 -translate-y-1/2 text-[#757575] w-5 h-5" />
                          <input
                            type="password"
                            placeholder="Enter current password"
                            value={currentPassword}
                            onChange={(e) => setCurrentPassword(e.target.value)}
                            className="w-full bg-[#F5F5F5] border-[1.33px] border-[#E0E0E0] rounded-lg h-[50.67px] pl-[40px] pr-4 text-[16px] text-[#1A1A1A] focus:outline-none focus:border-primary focus:ring-1 focus:ring-primary transition-colors"
                          />
                        </div>
                      </div>

                      {/* New Password */}
                      <div className="flex flex-col gap-2">
                        <label className="text-[14px] font-medium text-[#1A1A1A] leading-[20px]">New Password</label>
                        <div className="relative">
                          <Shield className="absolute left-4 top-1/2 -translate-y-1/2 text-[#757575] w-5 h-5" />
                          <input
                            type="password"
                            placeholder="Enter new password"
                            value={newPassword}
                            onChange={(e) => setNewPassword(e.target.value)}
                            className="w-full bg-[#F5F5F5] border-[1.33px] border-[#E0E0E0] rounded-lg h-[50.67px] pl-[40px] pr-4 text-[16px] text-[#1A1A1A] focus:outline-none focus:border-primary focus:ring-1 focus:ring-primary transition-colors"
                          />
                        </div>
                        <span className="text-[12px] text-[#757575] leading-[16px]">Use at least 8 characters with a mix of uppercase, lowercase, and numbers.</span>
                      </div>

                      {/* Confirm Password */}
                      <div className="flex flex-col gap-2 mb-2">
                        <label className="text-[14px] font-medium text-[#1A1A1A] leading-[20px]">Confirm New Password</label>
                        <div className="relative">
                          <Shield className="absolute left-4 top-1/2 -translate-y-1/2 text-[#757575] w-5 h-5" />
                          <input
                            type="password"
                            placeholder="Confirm new password"
                            value={confirmPassword}
                            onChange={(e) => setConfirmPassword(e.target.value)}
                            className="w-full bg-[#F5F5F5] border-[1.33px] border-[#E0E0E0] rounded-lg h-[50.67px] pl-[40px] pr-4 text-[16px] text-[#1A1A1A] focus:outline-none focus:border-primary focus:ring-1 focus:ring-primary transition-colors"
                          />
                        </div>
                      </div>

                      <button 
                        type="button" 
                        disabled={!currentPassword || !newPassword || !confirmPassword || newPassword !== confirmPassword}
                        className="h-[48px] bg-[#EEEEEE] text-[#757575] rounded-lg text-[16px] font-medium transition-colors w-full sm:w-auto px-8 self-start disabled:opacity-50"
                      >
                        Change Password
                      </button>
                    </form>
                  </div>

                  {/* Recent Activity */}
                  <div className="bg-white border-[1.33px] border-[#E0E0E0] rounded-lg p-[25.33px] flex flex-col gap-[16px]">
                    <h2 className="text-[20px] font-semibold text-[#1A1A1A] leading-[30px]">Recent Activity</h2>
                    
                    <div className="flex flex-col">
                      <div className="flex items-center justify-between py-3 border-b-[1.33px] border-[#E0E0E0]">
                        <div className="flex flex-col">
                          <span className="text-[14px] font-medium text-[#1A1A1A]">Login from Chrome on Windows</span>
                          <span className="text-[12px] font-normal text-[#757575]">IP: 192.168.1.100</span>
                        </div>
                        <span className="text-[12px] font-normal text-[#757575]">2 hours ago</span>
                      </div>

                      <div className="flex items-center justify-between py-3 border-b-[1.33px] border-[#E0E0E0]">
                        <div className="flex flex-col">
                          <span className="text-[14px] font-medium text-[#1A1A1A]">Password changed</span>
                          <span className="text-[12px] font-normal text-[#757575]">Security update</span>
                        </div>
                        <span className="text-[12px] font-normal text-[#757575]">1 day ago</span>
                      </div>

                      <div className="flex items-center justify-between py-3">
                        <div className="flex flex-col">
                          <span className="text-[14px] font-medium text-[#1A1A1A]">Login from Safari on iPhone</span>
                          <span className="text-[12px] font-normal text-[#757575]">IP: 192.168.1.150</span>
                        </div>
                        <span className="text-[12px] font-normal text-[#757575]">3 days ago</span>
                      </div>
                    </div>
                  </div>

                </div>
              )}

              {activeTab === 'PREFERENCES' && (
                <div className="bg-white border-[1.33px] border-[#E0E0E0] rounded-lg p-[25.33px] flex flex-col gap-[24px]">
                  <div className="flex flex-col gap-1">
                    <h2 className="text-[20px] font-semibold text-[#1A1A1A] leading-[30px]">Preferences</h2>
                    <p className="text-[14px] font-normal text-[#757575] leading-[20px]">Manage your notifications and display settings</p>
                  </div>
                  
                  <div className="text-[14px] text-[#757575]">
                    Notification settings coming soon.
                  </div>
                </div>
              )}

            </div>

          </div>
        </div>
      </main>
    </div>
  );
};
