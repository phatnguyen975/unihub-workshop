import React, { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { ArrowLeft, LockKeyhole } from 'lucide-react';

const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

type ForgotErrors = {
  email?: string;
};

export const ForgotPasswordPage: React.FC = () => {
  const navigate = useNavigate();
  const [email, setEmail] = useState('');
  const [errors, setErrors] = useState<ForgotErrors>({});

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    const trimmedEmail = email.trim();
    const nextErrors: ForgotErrors = {};

    if (!trimmedEmail) {
      nextErrors.email = 'Email is required.';
    } else if (!EMAIL_PATTERN.test(trimmedEmail)) {
      nextErrors.email = 'Enter a valid email address.';
    }

    setErrors(nextErrors);
    if (Object.keys(nextErrors).length > 0) {
      return;
    }

    navigate('/verify-reset');
  };

  return (
    <div
      className="min-h-screen flex items-center justify-center px-4 py-4"
      style={{
        background:
          'linear-gradient(135deg, #E8F5E9 0%, #EAF6EB 16.67%, #EBF6ED 33.33%, #EDF7EF 50%, #EEF7F0 66.67%, #F0F8F2 83.33%, #F1F8F4 100%)',
      }}
    >
      <div className="flex flex-col items-center w-full max-w-[447.99px] gap-[32px]">
        <div className="flex flex-col items-center">
          <div className="w-[63.99px] h-[63.99px] bg-primary rounded-lg flex items-center justify-center shadow-[0px_10px_15px_-3px_rgba(0,0,0,0.1),0px_4px_6px_-4px_rgba(0,0,0,0.1)] mb-[15.99px]">
            <LockKeyhole className="text-white w-[32px] h-[32px]" />
          </div>
          <h1 className="text-[30px] font-semibold text-[#1A1A1A] leading-[36px] tracking-[0.395508px] text-center mb-[3.99px]">
            Forgot Password?
          </h1>
          <p className="text-[16px] font-normal text-[#757575] leading-[24px] tracking-[-0.3125px] text-center">
            No worries, we'll send you reset instructions
          </p>
        </div>

        <div className="w-full bg-white rounded-[16px] border-[0.555556px] border-[#E0E0E0] shadow-[0px_20px_25px_-5px_rgba(0,0,0,0.1),0px_8px_10px_-6px_rgba(0,0,0,0.1)] px-[32.5521px] pt-[32.5521px] pb-[16.5538px] flex flex-col gap-[23.99px]">
          <form onSubmit={handleSubmit} noValidate className="flex flex-col gap-[23.99px]">
            <div className="flex flex-col gap-[7.99px]">
              <label className="text-[14px] font-medium text-[#1A1A1A] leading-[20px] tracking-[-0.150391px]">
                Email Address
              </label>
              <input
                type="email"
                placeholder="Enter your registered email"
                value={email}
                onChange={(e) => {
                  setEmail(e.target.value);
                  if (errors.email) {
                    setErrors((prev) => ({ ...prev, email: undefined }));
                  }
                }}
                className={`bg-[#F5F5F5] border-[0.555556px] ${
                  errors.email
                    ? 'border-[#C62828] focus:border-[#C62828] focus:ring-[#C62828]'
                    : 'border-[#E0E0E0] focus:border-primary focus:ring-primary'
                } rounded-[8px] h-[49.1px] px-[16px] py-[12px] text-[16px] leading-[19px] tracking-[-0.3125px] text-[#1A1A1A] placeholder:text-[rgba(26,26,26,0.5)] focus:outline-none focus:ring-1 transition-colors`}
              />
              {errors.email && (
                <p className="text-[12px] leading-[16px] text-[#C62828]">{errors.email}</p>
              )}
              <p className="text-[12px] leading-[16px] text-[#757575]">
                We'll send a password reset link to this email
              </p>
            </div>

            <button
              type="submit"
              className="w-full h-[47.99px] bg-primary text-white rounded-[8px] text-[16px] font-medium leading-[24px] tracking-[-0.3125px] flex items-center justify-center hover:bg-[#154d19] transition-colors"
            >
              Send Reset Link
            </button>

            <Link
              to="/login"
              className="w-full h-[49.1px] border-[0.555556px] border-[#E0E0E0] rounded-[8px] text-[16px] font-medium leading-[24px] tracking-[-0.3125px] text-[#1A1A1A] flex items-center justify-center gap-[7.99px]"
            >
              <ArrowLeft size={16} />
              Back to Login
            </Link>
          </form>
        </div>
      </div>
    </div>
  );
};
