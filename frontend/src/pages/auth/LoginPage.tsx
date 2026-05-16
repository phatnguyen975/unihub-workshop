import React, { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuthStore } from '../../hooks/useAuthStore';
import { MOCK_STUDENT_USER, MOCK_ORGANIZER_USER, DEMO_CREDENTIALS } from '../../constants/mockData';
import { Eye, EyeOff, BookOpen, AlertCircle } from 'lucide-react';
// import { authApi } from '../../api/auth'; // We'll use mock for now as requested

const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

type LoginErrors = {
  email?: string;
  password?: string;
};

export const LoginPage: React.FC = () => {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState('');
  const [errors, setErrors] = useState<LoginErrors>({});
  const navigate = useNavigate();
  const setAuth = useAuthStore((s) => s.setAuth);
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated);
  const user = useAuthStore((s) => s.user);

  React.useEffect(() => {
    if (isAuthenticated) {
      navigate(user?.role === 'ORGANIZER' ? '/organizer' : '/');
    }
  }, [isAuthenticated, navigate, user]);

  const handleLogin = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');

    const nextErrors: LoginErrors = {};
    const trimmedEmail = email.trim();
    const trimmedPassword = password.trim();

    if (!trimmedEmail) {
      nextErrors.email = 'Email is required.';
    } else if (!EMAIL_PATTERN.test(trimmedEmail)) {
      nextErrors.email = 'Enter a valid email address.';
    }

    if (!trimmedPassword) {
      nextErrors.password = 'Password is required.';
    }

    setErrors(nextErrors);
    if (Object.keys(nextErrors).length > 0) {
      return;
    }

    setIsLoading(true);

    try {
      // Simulate API call
      await new Promise((resolve) => setTimeout(resolve, 800));

      // Mock authentication logic
      const isStudent = email === DEMO_CREDENTIALS.student.email && password === DEMO_CREDENTIALS.student.password;
      const isOrganizer = email === DEMO_CREDENTIALS.organizer.email && password === DEMO_CREDENTIALS.organizer.password;
      const isTemp = email === DEMO_CREDENTIALS.temp.email && password === DEMO_CREDENTIALS.temp.password;

      if (isStudent || isTemp) {
        setAuth(MOCK_STUDENT_USER, 'mock-jwt-token-student');
        navigate('/');
      } else if (isOrganizer) {
        setAuth(MOCK_ORGANIZER_USER, 'mock-jwt-token-organizer');
        navigate('/organizer');
      } else {
        setError('Invalid email or password. Please try again.');
      }
    } catch (err) {
      setError('An error occurred during login. Please try again.');
    } finally {
      setIsLoading(false);
    }
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
        {/* Logo block */}
        <div className="flex flex-col items-center">
          <div className="w-[63.99px] h-[63.99px] bg-primary rounded-lg flex items-center justify-center shadow-[0px_10px_15px_-3px_rgba(0,0,0,0.1),0px_4px_6px_-4px_rgba(0,0,0,0.1)] mb-[15.99px]">
            <BookOpen className="text-white w-[32px] h-[32px]" />
          </div>
          <h1 className="text-[30px] font-semibold text-[#1A1A1A] leading-[36px] tracking-[0.395508px] text-center mb-[3.99px]">
            Welcome to UniHub
          </h1>
          <p className="text-[16px] font-normal text-[#757575] leading-[24px] tracking-[-0.3125px] text-center">
            Sign in to access your workshop portal
          </p>
        </div>

        {/* Login Card */}
        <div className="w-full bg-white rounded-[16px] border-[0.555556px] border-[#E0E0E0] shadow-[0px_20px_25px_-5px_rgba(0,0,0,0.1),0px_8px_10px_-6px_rgba(0,0,0,0.1)] px-[32.5521px] pt-[32.5521px] pb-[16.5538px] flex flex-col gap-[23.99px]">
          <form onSubmit={handleLogin} noValidate className="flex flex-col gap-[23.99px]">
            
            {/* Email Field */}
            <div className="flex flex-col gap-[7.99px]">
              <label className="text-[14px] font-medium text-[#1A1A1A] leading-[20px] tracking-[-0.150391px]">
                Email Address
              </label>
              <input
                type="email"
                value={email}
                onChange={(e) => {
                  setEmail(e.target.value);
                  if (errors.email) {
                    setErrors((prev) => ({ ...prev, email: undefined }));
                  }
                  if (error) {
                    setError('');
                  }
                }}
                placeholder="student@unihub.edu"
                className={`bg-[#F5F5F5] border-[0.555556px] ${
                  errors.email
                    ? 'border-[#C62828] focus:border-[#C62828] focus:ring-[#C62828]'
                    : 'border-[#E0E0E0] focus:border-primary focus:ring-primary'
                } rounded-[8px] h-[49.1px] px-[16px] py-[12px] text-[16px] leading-[19px] tracking-[-0.3125px] text-[#1A1A1A] placeholder:text-[rgba(26,26,26,0.5)] focus:outline-none focus:ring-1 transition-colors`}
              />
              {errors.email && (
                <p className="text-[12px] leading-[16px] text-[#C62828]">{errors.email}</p>
              )}
            </div>

            {/* Password Field */}
            <div className="flex flex-col gap-[7.99px]">
              <label className="text-[14px] font-medium text-[#1A1A1A] leading-[20px] tracking-[-0.150391px]">
                Password
              </label>
              <div className="relative">
                <input
                  type={showPassword ? 'text' : 'password'}
                  value={password}
                  onChange={(e) => {
                    setPassword(e.target.value);
                    if (errors.password) {
                      setErrors((prev) => ({ ...prev, password: undefined }));
                    }
                    if (error) {
                      setError('');
                    }
                  }}
                  placeholder="Enter your password"
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

            {/* Forgot Password */}
            <div className="flex justify-end">
              <Link
                to="/forgot-password"
                className="text-[14px] font-medium text-primary tracking-[-0.150391px] hover:underline"
              >
                Forgot Password?
              </Link>
            </div>

            {error && (
              <div className="flex items-center gap-[7.99px] h-[55.1px] px-[16.55px] rounded-[8px] border-[0.555556px] border-[#C62828] bg-[#FFCDD2]">
                <AlertCircle className="text-[#C62828]" size={20} />
                <span className="text-[14px] leading-[20px] tracking-[-0.150391px] text-[#C62828]">
                  {error}
                </span>
              </div>
            )}

            {/* Login Button */}
            <button
              type="submit"
              disabled={isLoading}
              className="w-full h-[47.99px] bg-primary text-white rounded-[8px] text-[16px] font-medium leading-[24px] tracking-[-0.3125px] flex items-center justify-center hover:bg-[#154d19] transition-colors disabled:opacity-70 disabled:cursor-not-allowed"
            >
              {isLoading ? (
                <div className="w-5 h-5 border-2 border-white border-t-transparent rounded-full animate-spin-custom"></div>
              ) : (
                'Login'
              )}
            </button>
          </form>

          {/* Divider & Register */}
          <div className="border-t-[0.555556px] border-[#E0E0E0] pt-[16.5538px] flex items-center justify-center gap-1">
            <span className="text-[14px] font-normal text-[#757575] leading-[20px] tracking-[-0.150391px]">
              Don't have an account?
            </span>
            <Link to="/register" className="text-[16px] font-medium text-primary hover:underline leading-[24px] tracking-[-0.3125px]">
              Register
            </Link>
          </div>
        </div>

        {/* Demo Credentials */}
        <div className="w-full bg-[#E3F2FD] border-[0.555556px] border-[#90CAF9] rounded-[8px] px-[16.5538px] pt-[16.5538px] pb-[16.5538px] flex flex-col gap-[7.99px]">
          <div className="text-[12px] font-semibold text-[#0D47A1] leading-[16px]">
            Demo Credentials:
          </div>
          <div className="flex flex-col gap-[3.99px]">
            <div className="text-[12px] font-normal text-[#1565C0] leading-[16px]">
              Student: student@unihub.edu / student123
            </div>
            <div className="text-[12px] font-normal text-[#1565C0] leading-[16px]">
              Organizer: organizer@unihub.edu / organizer123
            </div>
            <div className="text-[12px] font-normal text-[#1565C0] leading-[16px]">
              Temp Password: temp@unihub.edu / temp123
            </div>
          </div>
        </div>

      </div>
    </div>
  );
};
