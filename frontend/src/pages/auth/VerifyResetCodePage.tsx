import React, { useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { ShieldCheck } from 'lucide-react';

export const VerifyResetCodePage: React.FC = () => {
  const navigate = useNavigate();
  const [code, setCode] = useState<string[]>(Array(6).fill(''));
  const inputRefs = useRef<Array<HTMLInputElement | null>>([]);

  const handleChange = (index: number, value: string) => {
    if (!/^\d?$/.test(value)) {
      return;
    }

    const nextCode = [...code];
    nextCode[index] = value;
    setCode(nextCode);

    if (value && index < inputRefs.current.length - 1) {
      inputRefs.current[index + 1]?.focus();
    }
  };

  const handleKeyDown = (index: number, event: React.KeyboardEvent<HTMLInputElement>) => {
    if (event.key === 'Backspace' && !code[index] && index > 0) {
      inputRefs.current[index - 1]?.focus();
    }
  };

  const handlePaste = (event: React.ClipboardEvent<HTMLInputElement>) => {
    const pasted = event.clipboardData.getData('text').replace(/\D/g, '').slice(0, 6);
    if (!pasted) {
      return;
    }

    const nextCode = pasted.split('');
    while (nextCode.length < 6) {
      nextCode.push('');
    }

    setCode(nextCode);

    const nextIndex = Math.min(pasted.length, 5);
    inputRefs.current[nextIndex]?.focus();
  };

  const isComplete = code.every((digit) => digit !== '');

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
            <ShieldCheck className="text-white w-[32px] h-[32px]" />
          </div>
          <h1 className="text-[30px] font-semibold text-[#1A1A1A] leading-[36px] tracking-[0.395508px] text-center mb-[3.99px]">
            Verify Reset Code
          </h1>
          <p className="text-[16px] font-normal text-[#757575] leading-[24px] tracking-[-0.3125px] text-center">
            We've sent a 6-digit code to
            <br />
            nttien231@clc.fitus.e
          </p>
        </div>

        <div className="w-full bg-white rounded-[16px] border-[0.555556px] border-[#E0E0E0] shadow-[0px_20px_25px_-5px_rgba(0,0,0,0.1),0px_8px_10px_-6px_rgba(0,0,0,0.1)] px-[32.5521px] pt-[32.5521px] pb-[16.5538px] flex flex-col gap-[23.99px]">
          <div className="flex flex-col gap-[23.99px]">
            <div className="flex flex-col items-center gap-[16px]">
              <label className="text-[14px] font-medium text-[#1A1A1A] leading-[20px] tracking-[-0.150391px] text-center">
                Enter Verification Code
              </label>
              <div className="flex items-center justify-center gap-[12px] px-[17.474px]">
                {code.map((digit, index) => (
                  <input
                    key={`code-${index}`}
                    ref={(element) => {
                      inputRefs.current[index] = element;
                    }}
                    type="text"
                    inputMode="numeric"
                    maxLength={1}
                    value={digit}
                    onChange={(event) => handleChange(index, event.target.value)}
                    onKeyDown={(event) => handleKeyDown(index, event)}
                    onPaste={index === 0 ? handlePaste : undefined}
                    className="w-[47.99px] h-[56px] bg-[#F5F5F5] border-[1.66667px] border-[#E0E0E0] rounded-[8px] text-center text-[20px] font-semibold leading-[28px] tracking-[-0.449219px] text-[#1A1A1A] focus:outline-none focus:border-primary focus:ring-1 focus:ring-primary"
                  />
                ))}
              </div>
            </div>

            <button
              type="button"
              disabled={!isComplete}
              onClick={() => navigate('/reset-password')}
              className="w-full h-[47.99px] bg-primary text-white rounded-[8px] text-[16px] font-medium leading-[24px] tracking-[-0.3125px] flex items-center justify-center transition-colors disabled:opacity-50"
            >
              Verify Code
            </button>

            <div className="border-t-[0.555556px] border-[#E0E0E0] pt-[16.5538px] flex flex-col items-center gap-[7.99px]">
              <span className="text-[14px] font-normal text-[#757575] leading-[20px] tracking-[-0.150391px]">
                Didn't receive the code?
              </span>
              <span className="text-[14px] font-normal text-[#757575] leading-[20px] tracking-[-0.150391px]">
                Resend code in 56s
              </span>
            </div>
          </div>

          <div className="w-full bg-[#E3F2FD] border-[0.555556px] border-[#90CAF9] rounded-[8px] px-[16.5538px] pt-[16.5538px] pb-[16.5538px]">
            <div className="text-[12px] font-semibold text-[#0D47A1] leading-[16px]">
              Note: The verification code is valid for 10 minute
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};
