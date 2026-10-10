import { Activity, Clock4, Users, type LucideIcon } from "lucide-react";

/** Three promo cards stating real platform rules (PRD BR-002 limit, BR-003 10-minute hold), not invented offers. */
const BANNERS: { title: string; text: string; icon: LucideIcon; style: string }[] = [
  {
    title: "Fair for everyone",
    text: "Up to 4 tickets per person for each event, so more fans get in.",
    icon: Users,
    style: "bg-linear-to-br from-[#ff7a45] to-[#ff9f43] text-white",
  },
  {
    title: "Your seats, held",
    text: "Reserved tickets are held for 10 minutes while you check out.",
    icon: Clock4,
    style: "bg-linear-to-br from-[#1f1b2e] to-[#3a2f5c] text-white",
  },
  {
    title: "Live stock counts",
    text: "Ticket counts come straight from the reservation engine.",
    icon: Activity,
    style: "bg-linear-to-br from-[#06c3e0] to-[#19d3c5] text-white",
  },
];

export function PromoBanners() {
  return (
    <section className="relative z-10 mx-auto -mt-12 grid max-w-6xl gap-4 px-4 sm:-mt-14 sm:grid-cols-3 sm:px-6" aria-label="How flash sales work">
      {BANNERS.map(({ title, text, icon: Icon, style }) => (
        <div key={title} className={`flex min-h-36 flex-col justify-between rounded-3xl p-5 shadow-lift transition hover:-translate-y-1 ${style}`}>
          <span className="grid size-10 place-items-center rounded-2xl bg-white/20 ring-1 ring-white/30">
            <Icon className="size-5" aria-hidden />
          </span>
          <div className="mt-4">
            <h2 className="text-lg font-bold">{title}</h2>
            <p className="mt-1 text-sm text-white/85">{text}</p>
          </div>
        </div>
      ))}
    </section>
  );
}
