import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Eye, EyeOff, KeyRound } from 'lucide-react';
import { SuccessModal } from '../../components/common/SuccessModal';

const MIN_PASSWORD_LENGTH = 8;

type ResetErrors = {
  password?: string;
  confirmPassword?: string;
};

export const ResetPasswordPage: React.FC = () => {
  const navigate = useNavigate();
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [showConfirmPassword, setShowConfirmPassword] = useState(false);
  const [isSuccessOpen, setIsSuccessOpen] = useState(false);
  const [errors, setErrors] = useState<ResetErrors>({});

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();

    const nextErrors: ResetErrors = {};
    if (!password) {
      nextErrors.password = 'Password is required.';
    } else if (password.length < MIN_PASSWORD_LENGTH) {
      nextErrors.password = `Password must be at least ${MIN_PASSWORD_LENGTH} characters.`;
    }

    if (!confirmPassword) {
      nextErrors.confirmPassword = 'Please confirm your password.';
    } else if (password !== confirmPassword) {
      nextErrors.confirmPassword = 'Passwords do not match.';
    }

    setErrors(nextErrors);
    if (Object.keys(nextErrors).length > 0) {
      return;
    }

    setIsSuccessOpen(true);
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
            <KeyRound className="text-white w-[32px] h-[32px]" />
          </div>
          <h1 className="text-[30px] font-semibold text-[#1A1A1A] leading-[36px] tracking-[0.395508px] text-center mb-[3.99px]">
            Reset Password
          </h1>
          <p className="text-[16px] font-normal text-[#757575] leading-[24px] tracking-[-0.3125px] text-center">
            Create a new secure password for your account
          </p>
        </div>

        <div className="w-full bg-white rounded-[16px] border-[0.555556px] border-[#E0E0E0] shadow-[0px_20px_25px_-5px_rgba(0,0,0,0.1),0px_8px_10px_-6px_rgba(0,0,0,0.1)] px-[32.5521px] pt-[32.5521px] pb-[16.5538px] flex flex-col gap-[23.99px]">
          <form onSubmit={handleSubmit} noValidate className="flex flex-col gap-[23.99px]">
            <div className="flex flex-col gap-[7.99px]">
              <label className="text-[14px] font-medium text-[#1A1A1A] leading-[20px] tracking-[-0.150391px]">
                New Password
              </label>
              <div className="relative">
                <input
                  type={showPassword ? 'text' : 'password'}
                  placeholder="Create a strong password"
                  value={password}
                  onChange={(e) => {
                    setPassword(e.target.value);
                    if (errors.password) {
                      setErrors((prev) => ({ ...prev, password: undefined }));
                    }
                    if (errors.confirmPassword) {
                      setErrors((prev) => ({ ...prev, confirmPassword: undefined }));
                    }
                  }}
                  className={`w-full bg-[#F5F5F5] border-[0.555556px] ${
                    errors.password
                      ? 'border-[#C62828] focus:border-[#C62828] focus:ring-[#C62828]'
                      : 'border-[#E0E0E0] focus:border-primary focus:ring-primary'
                  } rounded-[8px] h-[49.1px] pl-[16px] pr-[48px] py-[12px] text-[16px] leading-[19px] tracking-[-0.3125px] text-[#1A1A1A] placeholder:text-[rgba(26,26,26,0.5)] focus:outline-none focus:ring-1 transition-colors`}
                />
                <button
                  type="button"
                  onClick={() => setShowPassword(!showPassword)}
                  className="absolute right-[16px] top-1/2 -translate-y-1/2 text-[#757575] hover:text-[#1A1A1A] transition-colors"
                  aria-label={showPassword ? 'Hide password' : 'Show password'}
                >
                  {showPassword ? <EyeOff size={20} /> : <Eye size={20} />}
                </button>
              </div>
              {errors.password && (
                <p className="text-[12px] leading-[16px] text-[#C62828]">{errors.password}</p>
              )}
            </div>

            <div className="flex flex-col gap-[7.99px]">
              <label className="text-[14px] font-medium text-[#1A1A1A] leading-[20px] tracking-[-0.150391px]">
                Confirm Password
              </label>
              <div className="relative">
                <input
                  type={showConfirmPassword ? 'text' : 'password'}
                  placeholder="Re-enter your password"
                  value={confirmPassword}
                  onChange={(e) => {
                    setConfirmPassword(e.target.value);
                    if (errors.confirmPassword) {
                      setErrors((prev) => ({ ...prev, confirmPassword: undefined }));
                    }
                  }}
                  className={`w-full bg-[#F5F5F5] border-[0.555556px] ${
                    errors.confirmPassword
                      ? 'border-[#C62828] focus:border-[#C62828] focus:ring-[#C62828]'
                      : 'border-[#E0E0E0] focus:border-primary focus:ring-primary'
                  } rounded-[8px] h-[49.1px] pl-[16px] pr-[48px] py-[12px] text-[16px] leading-[19px] tracking-[-0.3125px] text-[#1A1A1A] placeholder:text-[rgba(26,26,26,0.5)] focus:outline-none focus:ring-1 transition-colors`}
                />
                <button
                  type="button"
                  onClick={() => setShowConfirmPassword(!showConfirmPassword)}
                  className="absolute right-[16px] top-1/2 -translate-y-1/2 text-[#757575] hover:text-[#1A1A1A] transition-colors"
                  aria-label={showConfirmPassword ? 'Hide password' : 'Show password'}
                >
                  {showConfirmPassword ? <EyeOff size={20} /> : <Eye size={20} />}
                </button>
              </div>
              {errors.confirmPassword && (
                <p className="text-[12px] leading-[16px] text-[#C62828]">{errors.confirmPassword}</p>
              )}
            </div>

            <div className="w-full bg-[#E3F2FD] border-[0.555556px] border-[#90CAF9] rounded-[8px] px-[16.5538px] pt-[16.5538px] pb-[16.5538px] flex flex-col gap-[7.99px]">
              <div className="text-[14px] font-semibold text-[#0D47A1] leading-[20px] tracking-[-0.150391px]">
                Password Requirements:
              </div>
              <ul className="flex flex-col gap-[3.99px] text-[12px] leading-[16px] text-[#1565C0]">
                <li>Minimum 8 characters long</li>
              </ul>
            </div>

            <button
              type="submit"
              className="w-full h-[47.99px] bg-primary text-white rounded-[8px] text-[16px] font-medium leading-[24px] tracking-[-0.3125px] flex items-center justify-center hover:bg-[#154d19] transition-colors"
            >
              Reset Password
            </button>
          </form>
        </div>
      </div>

      <SuccessModal
        isOpen={isSuccessOpen}
        onClose={() => {
          setIsSuccessOpen(false);
          navigate('/login');
        }}
        title="Password Reset Successful!"
        message="Your password has been successfully reset. You can now log in with your new password."
        buttonText="Back to Login"
      />
    </div>
  );
};
